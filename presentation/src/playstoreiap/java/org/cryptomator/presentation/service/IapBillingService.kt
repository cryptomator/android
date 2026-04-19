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
import com.android.billingclient.api.QueryPurchasesParams
import org.cryptomator.presentation.R
import org.cryptomator.util.SharedPreferencesHandler
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

class IapBillingService : Service(), PurchasesUpdatedListener {

	private val fullVersionProductId = ProductInfo.PRODUCT_FULL_VERSION
	private val yearlySubscriptionProductId = ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION

	private lateinit var billingClient: BillingClient
	private lateinit var sharedPreferencesHandler: SharedPreferencesHandler
	private lateinit var purchaseManager: PurchaseManager
	private lateinit var purchaseRefreshCoordinator: PurchaseRefreshCoordinator

	private val productDetailsMap = ConcurrentHashMap<String, ProductDetails>()
	private val pendingProductDetailsCallbacks = mutableListOf<(List<ProductInfo>) -> Unit>()

	/**
	 * Initializes billing-related components, configures the Google Play BillingClient, and starts the billing connection.
	 *
	 * When the connection is successfully established, existing purchases are queried/restored and any queued
	 * product-details callbacks are flushed by querying product details and invoking each queued callback with the results.
	 *
	 * @param context Context used to create the billing client and related helpers.
	 */
	private fun initBillingClient(context: Context) {
		this.sharedPreferencesHandler = SharedPreferencesHandler(context)
		this.purchaseManager = PurchaseManager(sharedPreferencesHandler)
		this.purchaseRefreshCoordinator = PurchaseRefreshCoordinator(sharedPreferencesHandler)
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
					synchronized(pendingProductDetailsCallbacks) {
						if (pendingProductDetailsCallbacks.isNotEmpty()) {
							val callbacks = ArrayList(pendingProductDetailsCallbacks)
							pendingProductDetailsCallbacks.clear()
							queryProductDetails { products ->
								callbacks.forEach { it(products) }
							}
						}
					}
				} else {
					Timber.tag("IapBillingService").e("Billing setup not successful, error: %d", billingResult.responseCode)
				}
			}

			override fun onBillingServiceDisconnected() {
				Timber.tag("IapBillingService").i("Billing service disconnected")
			}
		})
	}

	/**
	 * Called when the service is created.
	 *
	 * Performs standard service initialization for this Service implementation.
	 */
	override fun onCreate() {
		super.onCreate()
		Timber.tag("IapBillingService").d("Service created")
	}

	/**
	 * Initiates a restore/refresh of existing purchases and acknowledges any pending transactions.
	 *
	 * @param onComplete Callback invoked with the resulting `RestoreOutcome` when the refresh completes.
	 */
	fun queryExistingPurchases(onComplete: (RestoreOutcome) -> Unit = {}) {
		purchaseRefreshCoordinator.refresh(
			billingClient = billingClient,
			purchaseManager = purchaseManager,
			acknowledge = { token -> acknowledgePurchase(token) },
			onComplete = onComplete,
		)
	}

	/**
	 * Acknowledges a completed purchase with Google Play Billing and retries once on transient failures.
	 *
	 * Sends an acknowledge request for the given purchase token to the configured BillingClient. If the
	 * billing response indicates a transient failure (service disconnected, service unavailable, or
	 * generic error), the method retries the acknowledge exactly once.
	 *
	 * @param purchaseToken The purchase token to acknowledge.
	 * @param isRetry Internal flag indicating this invocation is a retry; callers should not set this to `true`.
	 */
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

	/**
	 * Fetches product details for the configured INAPP and SUBS products and delivers an aggregated list of ProductInfo to the provided callback.
	 *
	 * The method queries INAPP and SUBS product details separately (per Billing Library requirements), caches returned ProductDetails, and invokes `callback` once both queries complete. If the billing client is not ready, the callback is enqueued and will be invoked after billing initialization completes.
	 *
	 * @param callback Invoked with a list of ProductInfo where each entry contains the product ID and its formatted price (empty string if unavailable).
	 */
	fun queryProductDetails(callback: (List<ProductInfo>) -> Unit) {
		if (!billingClient.isReady) {
			synchronized(pendingProductDetailsCallbacks) {
				pendingProductDetailsCallbacks.add(callback)
			}
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

		// Query INAPP products
		val inappParams = QueryProductDetailsParams.newBuilder().setProductList(
			listOf(
				QueryProductDetailsParams.Product.newBuilder()
					.setProductId(fullVersionProductId)
					.setProductType(BillingClient.ProductType.INAPP)
					.build()
			)
		).build()
		billingClient.queryProductDetailsAsync(inappParams) { billingResult, productDetailsResult ->
			if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
				synchronized(lock) {
					for (productDetails in productDetailsResult.productDetailsList) {
						productDetailsMap[productDetails.productId] = productDetails
						results.add(
							ProductInfo(
								productDetails.productId,
								productDetails.oneTimePurchaseOfferDetails?.formattedPrice ?: ""
							)
						)
					}
				}
			}
			onQueryComplete()
		}

		// Query SUBS products (must be separate — Billing Library requires same product type per query)
		val subsParams = QueryProductDetailsParams.newBuilder().setProductList(
			listOf(
				QueryProductDetailsParams.Product.newBuilder()
					.setProductId(yearlySubscriptionProductId)
					.setProductType(BillingClient.ProductType.SUBS)
					.build()
			)
		).build()
		billingClient.queryProductDetailsAsync(subsParams) { billingResult, productDetailsResult ->
			if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
				synchronized(lock) {
					for (productDetails in productDetailsResult.productDetailsList) {
						productDetailsMap[productDetails.productId] = productDetails
						val pricingPhase = productDetails.subscriptionOfferDetails
							?.firstOrNull()
							?.pricingPhases
							?.pricingPhaseList
							?.firstOrNull()
						results.add(
							ProductInfo(
								productDetails.productId,
								pricingPhase?.formattedPrice ?: ""
							)
						)
					}
				}
			}
			onQueryComplete()
		}
	}

	/**
	 * Initiates the Play Billing flow for the given product using cached product details.
	 *
	 * If product details for the specified productId are not available, shows a short toast
	 * informing the user that the purchase is not available and does nothing else.
	 *
	 * @param activity WeakReference to an Activity used to display UI and to launch the billing flow.
	 * @param productId The product identifier to purchase; must match an entry in the cached product details.
	 */
	fun launchPurchaseFlow(activity: WeakReference<Activity>, productId: String) {
		val details = productDetailsMap[productId]
		if (details == null) {
			Timber.tag("IapBillingService").w("Product details not loaded for %s", productId)
			activity.get()?.let { act ->
				act.runOnUiThread {
					Toast.makeText(act, R.string.error_purchase_not_available, Toast.LENGTH_SHORT).show()
				}
			}
			return
		}
		val paramsBuilder = ProductDetailsParams.newBuilder().setProductDetails(details)
		if (details.productType == BillingClient.ProductType.SUBS) {
			details.subscriptionOfferDetails?.firstOrNull()?.offerToken?.let {
				paramsBuilder.setOfferToken(it)
			}
		}
		val billingFlowParams = BillingFlowParams.newBuilder()
			.setProductDetailsParamsList(listOf(paramsBuilder.build()))
			.build()
		activity.get()?.let { billingClient.launchBillingFlow(it, billingFlowParams) }
	}

	/**
	 * Handles purchase updates from the Play Billing library and acts on their outcome.
	 *
	 * Routes successful purchases to the purchase manager for processing and acknowledges them.
	 * Logs user cancellations and logs failures along with the billing response code.
	 *
	 * @param billingResult The billing result containing the response code and additional info.
	 * @param purchases The list of updated purchases, or `null` if none are provided.
	 */
	override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
		if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
			purchaseManager.handleInAppPurchases(purchases) { token -> acknowledgePurchase(token) }
			purchaseManager.handleSubscriptionPurchases(purchases) { token -> acknowledgePurchase(token) }
		} else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
			Timber.tag("IapBillingService").i("User canceled purchase flow")
		} else {
			Timber.tag("IapBillingService").e("Purchase update failed")
			Timber.tag("IapBillingService").d("Purchase update error response code: %d", billingResult.responseCode)
		}
	}

	/**
	 * Cleans up resources by ending the billing client connection if initialized and logs service destruction.
	 *
	 * This method ensures the BillingClient connection is closed to avoid leaks before the service is destroyed.
	 */
	override fun onDestroy() {
		super.onDestroy()
		if (::billingClient.isInitialized) {
			billingClient.endConnection()
		}
		Timber.tag("IapBillingService").i("Service destroyed")
	}

	/**
 * Provide a Binder instance that clients use to interact with this service.
 *
 * @return An IBinder exposing the service's Binder tied to this IapBillingService instance.
 */
override fun onBind(intent: Intent?): IBinder = Binder(this)

	class Binder(private val service: IapBillingService) : android.os.Binder() {

		/**
		 * Initializes the billing client and purchase-related components using the provided Context.
		 *
		 * @param context Context used to initialize the billing client (prefer the application context).
		 */
		fun init(context: Context) {
			service.initBillingClient(context)
		}

		/**
		 * Starts the purchase flow for the specified product using the given Activity reference.
		 *
		 * @param activity A weak reference to the Activity used to launch the billing UI; may be cleared if the Activity is no longer available.
		 * @param productId The product identifier to purchase. 
		 */
		fun startPurchaseFlow(activity: WeakReference<Activity>, productId: String) {
			service.launchPurchaseFlow(activity, productId)
		}

		/**
		 * Requests current product details and invokes the callback with the results when available.
		 *
		 * The callback receives a list of ProductInfo entries containing product IDs and their formatted prices.
		 *
		 * @param callback Invoked with the retrieved list of ProductInfo once the query completes.
		 */
		fun queryProductDetails(callback: (List<ProductInfo>) -> Unit) {
			service.queryProductDetails(callback)
		}

		/**
		 * Requests restoration (refresh) of existing purchases and invokes the provided callback with the result.
		 *
		 * @param onComplete Callback invoked with the resulting [RestoreOutcome] once the restore/refresh operation completes.
		 */
		fun restorePurchases(onComplete: (RestoreOutcome) -> Unit) {
			service.queryExistingPurchases(onComplete)
		}
	}
}
