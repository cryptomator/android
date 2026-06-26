package org.cryptomator.presentation.service

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import org.cryptomator.presentation.R
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.util.SharedPreferencesHandler
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

class IapBillingService : Service(), PurchasesUpdatedListener {

	private lateinit var billingClient: BillingClient
	private lateinit var sharedPreferencesHandler: SharedPreferencesHandler
	private lateinit var purchaseManager: PurchaseManager
	private lateinit var purchaseRefreshCoordinator: PurchaseRefreshCoordinator

	private val productDetailsMap = ConcurrentHashMap<String, ProductDetails>()
	private val pendingProductDetailsCallbacks = PendingCallbackQueue<List<ProductInfo>>()

	private fun initBillingClient(context: Context) {
		this.sharedPreferencesHandler = SharedPreferencesHandler(context)
		this.purchaseManager = PurchaseManager(sharedPreferencesHandler)
		this.purchaseRefreshCoordinator = PurchaseRefreshCoordinator(sharedPreferencesHandler, LicenseEnforcer(sharedPreferencesHandler))
		val pendingPurchasesParams = PendingPurchasesParams.newBuilder()
			.enableOneTimeProducts()
			.enablePrepaidPlans()
			.build()
		billingClient = BillingClient.newBuilder(context)
			.setListener(this)
			.enablePendingPurchases(pendingPurchasesParams)
			.enableAutoServiceReconnection()
			.build()
		billingClient.startConnection(object : BillingClientStateListener {
			override fun onBillingSetupFinished(billingResult: BillingResult) {
				if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
					Timber.tag("IapBillingService").d("Billing setup successful")
					queryExistingPurchases()
					pendingProductDetailsCallbacks.drainSnapshot()?.let { callbacks ->
						queryProductDetails { products -> callbacks.forEach { it(products) } }
					}
				} else {
					Timber.tag("IapBillingService").e("Billing setup not successful, error: %d", billingResult.responseCode)
					pendingProductDetailsCallbacks.drainSnapshot()?.forEach { it(emptyList()) }
				}
			}

			override fun onBillingServiceDisconnected() {
				Timber.tag("IapBillingService").i("Billing service disconnected")
			}
		})
	}

	override fun onCreate() {
		super.onCreate()
		Timber.tag("IapBillingService").d("Service created")
	}

	fun queryExistingPurchases(onComplete: (RestoreOutcome) -> Unit = {}) {
		purchaseRefreshCoordinator.refresh(
			billingClient = billingClient,
			purchaseManager = purchaseManager,
			acknowledge = { token -> acknowledgePurchase(token) },
			onComplete = onComplete,
		)
	}

	private fun acknowledgePurchase(purchaseToken: String, isRetry: Boolean = false) {
		val params = AcknowledgePurchaseParams.newBuilder()
			.setPurchaseToken(purchaseToken)
			.build()
		billingClient.acknowledgePurchase(params) { billingResult ->
			when (billingResult.responseCode) {
				BillingClient.BillingResponseCode.OK -> {
					Timber.tag("IapBillingService").d("Purchase acknowledged")
				}
				BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
				BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
				BillingClient.BillingResponseCode.ERROR -> {
					Timber.tag("IapBillingService").e("Acknowledge failed with transient error")
					Timber.tag("IapBillingService").d("Acknowledge error response code: %d", billingResult.responseCode)
					if (!isRetry) {
						acknowledgePurchase(purchaseToken, isRetry = true)
					}
				}
				else -> {
					Timber.tag("IapBillingService").e("Acknowledge failed with permanent error")
					Timber.tag("IapBillingService").d("Acknowledge error response code: %d", billingResult.responseCode)
				}
			}
		}
	}

	private fun findPromotionalInappOffer(offerDetails: List<ProductDetails.OneTimePurchaseOfferDetails>?): ProductDetails.OneTimePurchaseOfferDetails? {
		return offerDetails?.firstOrNull { it.offerId != null }
	}

	private fun selectSubscriptionOffer(offerDetails: List<ProductDetails.SubscriptionOfferDetails>?): ProductDetails.SubscriptionOfferDetails? {
		return offerDetails?.firstOrNull()
	}

	fun queryProductDetails(callback: (List<ProductInfo>) -> Unit) {
		if (!billingClient.isReady) {
			pendingProductDetailsCallbacks.enqueue(callback)
			return
		}
		val lock = Any()
		val results = mutableListOf<ProductInfo>()
		var queriesCompleted = 0
		val totalQueries = 2

		fun onQueryComplete() {
			val readyResults: List<ProductInfo>?
			synchronized(lock) {
				queriesCompleted++
				readyResults = if (queriesCompleted == totalQueries) ArrayList(results) else null
			}
			readyResults?.let { callback(it) }
		}

		fun doQuery(params: QueryProductDetailsParams, buildProductInfo: (ProductDetails) -> ProductInfo?) {
			billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
				if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
					synchronized(lock) {
						for (productDetails in productDetailsResult.productDetailsList) {
							productDetailsMap[productDetails.productId] = productDetails
							buildProductInfo(productDetails)?.let { results.add(it) }
						}
					}
				}
				onQueryComplete()
			}
		}

		doQuery(
			QueryProductDetailsParams.newBuilder().setProductList(
				listOf(
					QueryProductDetailsParams.Product.newBuilder()
						.setProductId(ProductInfo.PRODUCT_FULL_VERSION)
						.setProductType(BillingClient.ProductType.INAPP)
						.build()
				)
			).build()
		) { details ->
			val baseOffer = details.oneTimePurchaseOfferDetailsList?.firstOrNull { it.offerId == null }
			val promoOffer = findPromotionalInappOffer(details.oneTimePurchaseOfferDetailsList)
			if (baseOffer != null && promoOffer != null) {
				val discountPercent = promoOffer.discountDisplayInfo?.percentageDiscount
				val discountEndTimeMillis = promoOffer.validTimeWindow?.endTimeMillis
				ProductInfo(details.productId, baseOffer.formattedPrice, promoOffer.formattedPrice, discountPercent, discountEndTimeMillis)
			} else {
				val price = baseOffer?.formattedPrice ?: promoOffer?.formattedPrice
				price?.let { ProductInfo(details.productId, it) }
			}
		}

		// Query SUBS products (must be separate — Billing Library requires same product type per query)
		doQuery(
			QueryProductDetailsParams.newBuilder().setProductList(
				listOf(
					QueryProductDetailsParams.Product.newBuilder()
						.setProductId(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION)
						.setProductType(BillingClient.ProductType.SUBS)
						.build()
				)
			).build()
		) { details ->
			val offer = selectSubscriptionOffer(details.subscriptionOfferDetails)
			val recurringPhase = offer?.pricingPhases?.pricingPhaseList?.lastOrNull { it.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING }
				?: offer?.pricingPhases?.pricingPhaseList?.lastOrNull()
			recurringPhase?.formattedPrice?.let { ProductInfo(details.productId, it) }
		}
	}

	fun launchPurchaseFlow(activity: WeakReference<Activity>, productId: String) {
		val details = productDetailsMap[productId]
		if (details == null) {
			Timber.tag("IapBillingService").w("Product details not loaded for %s", productId)
			activity.get()?.let { Toast.makeText(it, R.string.error_purchase_not_available, Toast.LENGTH_SHORT).show() }
			return
		}
		val paramsBuilder = ProductDetailsParams.newBuilder().setProductDetails(details)
		if (details.productType == BillingClient.ProductType.SUBS) {
			selectSubscriptionOffer(details.subscriptionOfferDetails)?.offerToken?.let { paramsBuilder.setOfferToken(it) }
		} else if (details.productType == BillingClient.ProductType.INAPP) {
			val promoOffer = findPromotionalInappOffer(details.oneTimePurchaseOfferDetailsList)
				?: details.oneTimePurchaseOfferDetailsList?.firstOrNull()
			promoOffer?.offerToken?.let { paramsBuilder.setOfferToken(it) }
		}
		val billingFlowParams = BillingFlowParams.newBuilder()
			.setProductDetailsParamsList(listOf(paramsBuilder.build()))
			.build()
		activity.get()?.let { billingClient.launchBillingFlow(it, billingFlowParams) }
	}

	override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
		if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
			purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, purchases) { token -> acknowledgePurchase(token) }
			purchaseManager.handlePurchases(PurchaseManager.Kind.SUBSCRIPTION, purchases) { token -> acknowledgePurchase(token) }
		} else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
			Timber.tag("IapBillingService").i("User canceled purchase flow")
		} else {
			Timber.tag("IapBillingService").e("Purchase update failed")
			Timber.tag("IapBillingService").d("Purchase update error response code: %d", billingResult.responseCode)
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		if (::billingClient.isInitialized) {
			billingClient.endConnection()
		}
		Timber.tag("IapBillingService").i("Service destroyed")
	}

	override fun onBind(intent: Intent?): IBinder = Binder(this)

	class Binder(private val service: IapBillingService) : android.os.Binder() {

		fun init(context: Context) {
			service.initBillingClient(context)
		}

		fun startPurchaseFlow(activity: WeakReference<Activity>, productId: String) {
			service.launchPurchaseFlow(activity, productId)
		}

		fun queryProductDetails(callback: (List<ProductInfo>) -> Unit) {
			service.queryProductDetails(callback)
		}

		fun restorePurchases(onComplete: (RestoreOutcome) -> Unit) {
			service.queryExistingPurchases(onComplete)
		}
	}
}
