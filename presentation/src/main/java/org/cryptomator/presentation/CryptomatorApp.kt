package org.cryptomator.presentation

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.StrictMode
import androidx.appcompat.app.AppCompatDelegate
import androidx.multidex.MultiDexApplication
import org.cryptomator.data.cloud.crypto.Cryptors
import org.cryptomator.data.cloud.crypto.CryptorsModule
import org.cryptomator.data.repository.RepositoryModule
import org.cryptomator.domain.Cloud
import org.cryptomator.presentation.di.HasComponent
import org.cryptomator.presentation.di.component.ApplicationComponent
import org.cryptomator.presentation.di.component.DaggerApplicationComponent
import org.cryptomator.presentation.di.module.ApplicationModule
import org.cryptomator.presentation.di.module.ThreadModule
import org.cryptomator.presentation.logging.CrashLogging.Companion.setup
import org.cryptomator.presentation.logging.DebugLogger
import org.cryptomator.presentation.logging.ReleaseLogger
import org.cryptomator.presentation.service.AutoUploadNotification
import org.cryptomator.presentation.service.AutoUploadService
import org.cryptomator.presentation.service.CryptorsService
import org.cryptomator.presentation.service.IapBillingService
import org.cryptomator.presentation.service.ProductInfo
import org.cryptomator.presentation.service.PurchaseRevokedToastObserver
import org.cryptomator.presentation.service.RestoreOutcome
import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.NoOpActivityLifecycleCallbacks
import org.cryptomator.util.SharedPreferencesHandler
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import io.reactivex.plugins.RxJavaPlugins
import timber.log.Timber

class CryptomatorApp : MultiDexApplication(), HasComponent<ApplicationComponent> {

	private val appCryptors = Cryptors.Delegating()
	private lateinit var applicationComponent: ApplicationComponent

	@Volatile
	private var cryptoServiceBinder: CryptorsService.Binder? = null

	@Volatile
	private var autoUploadServiceBinder: AutoUploadService.Binder? = null

	@Volatile
	private var iapBillingServiceBinder: IapBillingService.Binder? = null

	@Volatile
	var lastRestoreOutcome: RestoreOutcome? = null

	/**
	 * Retrieve and clear the last stored restore outcome.
	 *
	 * @return The last stored `RestoreOutcome`, or `null` if none was set.
	 */
	fun consumeLastRestoreOutcome(): RestoreOutcome? {
		val outcome = lastRestoreOutcome
		lastRestoreOutcome = null
		return outcome
	}

	private val pendingProductDetailsCallbacks = mutableListOf<(List<ProductInfo>) -> Unit>()

	/**
	 * Performs application startup and global initialization for the app process.
	 *
	 * Initializes logging and crash reporting, determines and logs build flavor and device info, initializes dependency injection, starts and binds background services, registers activity lifecycle callbacks (including the purchase-revoked observer only for freemium builds), applies saved UI night-mode, runs cache cleanup, applies an optional Microsoft workaround VmPolicy, and installs a global RxJava error handler.
	 */
	override fun onCreate() {
		super.onCreate()
		setupLogging()
		val sharedPreferencesHandler = SharedPreferencesHandler(applicationContext())
		@Suppress("KotlinConstantConditions") //
		val flavor = when (BuildConfig.FLAVOR) {
			"apkstore" -> "APK Store Edition"
			"fdroid" -> "F-Droid Edition"
			"lite" -> "F-Droid Main Repo Edition"
			"accrescent" -> "Accrescent Edition"
			"playstoreiap" -> "IAP Google Play Edition"
			else -> "Google Play Edition"
		}
		Timber.tag("App").i(
			"Cryptomator v%s (%d) \"%s\" started on android %s / API%d using a %s",  //
			BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, flavor,  //
			Build.VERSION.RELEASE, Build.VERSION.SDK_INT,  //
			Build.MODEL
		)
		Timber.tag("App").d("appId %s", BuildConfig.APPLICATION_ID)

		initializeInjector()
		launchServices()
		registerActivityLifecycleCallbacks(serviceNotifier)
		if (FlavorConfig.isFreemiumFlavor) {
			registerActivityLifecycleCallbacks(PurchaseRevokedToastObserver(sharedPreferencesHandler))
		}
		AppCompatDelegate.setDefaultNightMode(sharedPreferencesHandler.screenStyleMode)
		cleanupCache()

		if (sharedPreferencesHandler.microsoftWorkaround()) {
			val builder: StrictMode.VmPolicy.Builder = StrictMode.VmPolicy.Builder()
			StrictMode.setVmPolicy(builder.build())
		}

		RxJavaPlugins.setErrorHandler { e: Throwable? -> Timber.tag("CryptomatorApp").e(e, "BaseErrorHandler detected a problem") }
	}

	/**
	 * Starts the application's background services used across the app.
	 *
	 * Attempts to start and bind the cryptors service, the IAP billing service (freemium builds only),
	 * and the auto-upload service. If starting a service throws an IllegalStateException, the error
	 * is logged and startup continues for the remaining services.
	 */
	private fun launchServices() {
		try {
			startCryptorsService()
		} catch (e: IllegalStateException) {
			Timber.tag("App").e(e, "Failed to launch cryptors service")
		}
		try {
			startIapBillingService()
		} catch (e: IllegalStateException) {
			Timber.tag("App").e(e, "Failed to launch IAP billing service")
		}
		try {
			startAutoUploadService()
		} catch (e: IllegalStateException) {
			Timber.tag("App").e(e, "Failed to launch auto upload service")
		}
	}

	/**
	 * Starts and binds to the CryptorsService and wires its runtime state into the application.
	 *
	 * On connection, stores the service binder, assigns the service's `Cryptors` instance as the app delegate,
	 * provides the service with the application's `FileUtil`, and calls `updateService()`.
	 * On disconnection, clears the stored binder and removes the app cryptors delegate.
	 */
	private fun startCryptorsService() {
		bindService(Intent(this, CryptorsService::class.java), object : ServiceConnection {
			override fun onServiceConnected(name: ComponentName, service: IBinder) {
				Timber.tag("App").i("Cryptors service connected")
				cryptoServiceBinder = service as CryptorsService.Binder
				cryptoServiceBinder?.let {
					appCryptors.setDelegate(it.cryptors())
					it.setFileUtil(applicationComponent.fileUtil())
				}
				updateService()
			}

			override fun onServiceDisconnected(name: ComponentName) {
				Timber.tag("App").i("Cryptors service disconnected")
				cryptoServiceBinder = null
				appCryptors.removeDelegate()
			}
		}, BIND_AUTO_CREATE)
	}

	/**
	 * Starts and binds to the in-app-purchase (IAP) billing service on freemium builds.
	 *
	 * If the current build flavor is not freemium, the method logs the decision and returns without binding.
	 * When the service connects, the app stores and initializes the billing binder and delivers any queued
	 * product-detail callbacks. When the service disconnects, the stored binder is cleared.
	 */
	private fun startIapBillingService() {
		if (!FlavorConfig.isFreemiumFlavor) {
			Timber.tag("App").d("IAP billing service skipped for flavor %s", BuildConfig.FLAVOR)
			return
		}
		bindService(Intent(this, IapBillingService::class.java), object : ServiceConnection {
			override fun onServiceConnected(name: ComponentName, service: IBinder) {
				Timber.tag("App").i("IAP Billing service connected")
				iapBillingServiceBinder = service as IapBillingService.Binder
				iapBillingServiceBinder?.init(Companion.applicationContext)
				drainPendingProductDetailsCallbacks()
			}

			override fun onServiceDisconnected(name: ComponentName) {
				Timber.tag("App").i("IAP Billing service disconnected")
				iapBillingServiceBinder = null
			}
		}, BIND_AUTO_CREATE)
	}

	/**
	 * Starts the in-app purchase flow for the specified product when running the freemium flavor.
	 *
	 * This is a no-op on non-freemium builds or if the billing service is not connected.
	 *
	 * @param activity A weak reference to the Activity used to launch the purchase UI.
	 * @param productId The identifier of the product to purchase.
	 */
	fun launchPurchaseFlow(activity: WeakReference<Activity>, productId: String) {
		if (FlavorConfig.isFreemiumFlavor) {
			iapBillingServiceBinder?.startPurchaseFlow(activity, productId)
		}
	}

	/**
	 * Fetches in-app product details and delivers them to the provided callback.
	 *
	 * On freemium builds, the callback is invoked with retrieved products if the billing service is connected;
	 * otherwise the callback is queued and will be invoked once the service connects. On non-freemium builds,
	 * the callback is invoked immediately with an empty list.
	 *
	 * @param callback Called with the list of available `ProductInfo` objects.
	 */
	fun queryProductDetails(callback: (List<ProductInfo>) -> Unit) {
		if (FlavorConfig.isFreemiumFlavor) {
			synchronized(pendingProductDetailsCallbacks) {
				iapBillingServiceBinder?.queryProductDetails(callback) ?: pendingProductDetailsCallbacks.add(callback)
			}
		} else {
			callback(emptyList())
		}
	}

	/**
	 * Delivers queued product-details callbacks by querying the in-app billing service and invoking each callback with the resulting product list.
	 *
	 * Queued callbacks are cleared after delivery. If the billing service binder is not connected, callbacks are cleared without being invoked.
	 */
	private fun drainPendingProductDetailsCallbacks() {
		synchronized(pendingProductDetailsCallbacks) {
			if (pendingProductDetailsCallbacks.isEmpty()) {
				return
			}
			val callbacks = ArrayList(pendingProductDetailsCallbacks)
			pendingProductDetailsCallbacks.clear()
			iapBillingServiceBinder?.queryProductDetails { products ->
				callbacks.forEach { it(products) }
			}
		}
	}

	/**
	 * Attempts to restore purchases and reports the result via the provided callback.
	 *
	 * If the build is not the freemium flavor, the callback is invoked immediately with
	 * `RestoreOutcome.NOTHING_TO_RESTORE`. If the in-app billing service is not connected,
	 * the callback is invoked with `RestoreOutcome.FAILED`. Otherwise the connected IAP
	 * service is used and its resulting outcome is forwarded to the callback.
	 *
	 * @param onComplete Callback invoked with the resulting `RestoreOutcome`.
	 */
	fun restorePurchases(onComplete: (RestoreOutcome) -> Unit = {}) {
		if (!FlavorConfig.isFreemiumFlavor) {
			onComplete(RestoreOutcome.NOTHING_TO_RESTORE)
			return
		}
		val binder = iapBillingServiceBinder
		if (binder == null) {
			Timber.tag("App").w("restorePurchases called before IAP binder ready")
			onComplete(RestoreOutcome.FAILED())
			return
		}
		binder.restorePurchases(onComplete)
	}

	/**
	 * Binds to the AutoUploadService and initializes the service binder when connected.
	 *
	 * When the service connects, stores the binder in `autoUploadServiceBinder` and calls its
	 * `init(...)` method with the app's cloud/content/file utilities and application context.
	 * Logs connection and disconnection events.
	 */
	private fun startAutoUploadService() {
		bindService(Intent(this, AutoUploadService::class.java), object : ServiceConnection {
			override fun onServiceConnected(name: ComponentName, service: IBinder) {
				Timber.tag("App").i("Auto upload service connected")
				autoUploadServiceBinder = service as AutoUploadService.Binder
				autoUploadServiceBinder?.init( //
					applicationComponent.cloudContentRepository(),  //
					applicationComponent.fileUtil(),  //
					applicationComponent.contentResolverUtil(),  //
					Companion.applicationContext
				)
			}

			override fun onServiceDisconnected(name: ComponentName) {
				Timber.tag("App").i("Auto upload service disconnected")
			}
		}, BIND_AUTO_CREATE)
	}

	fun startAutoUpload(cloud: Cloud) {
		autoUploadServiceBinder?.startUpload(cloud)
	}

	fun startAutoUpload() {
		val sharedPreferencesHandler = SharedPreferencesHandler(applicationContext())
		if (checkToStartAutoImageUpload(sharedPreferencesHandler)) {
			val vault = try {
				applicationComponent.vaultRepository().load(sharedPreferencesHandler.photoUploadVault())
			} catch (e: NullPointerException) {
				null
			}
			if (vault?.isUnlocked == true) {
				val cloud = applicationComponent.cloudRepository().decryptedViewOf(vault)
				startAutoUpload(cloud)
			} else if (vault == null) {
				autoUploadServiceBinder?.vaultNotFound() ?: run {
					Timber.tag("App").i("autoUploadServiceBinder not yet initialized, manually show notification")
					AutoUploadNotification(applicationContext, 0).showVaultNotFoundNotification()
				}
			}
		}
	}

	private fun checkToStartAutoImageUpload(sharedPreferencesHandler: SharedPreferencesHandler): Boolean {
		return sharedPreferencesHandler.usePhotoUpload() //
				&& (!sharedPreferencesHandler.autoPhotoUploadOnlyUsingWifi() || applicationComponent.networkConnectionCheck().checkWifiOnAndConnected())
	}

	private fun setupLogging() {
		setupLoggingFramework()
		setup()
	}

	private fun initializeInjector() {
		applicationComponent = DaggerApplicationComponent.builder() //
			.applicationModule(ApplicationModule(this)) //
			.threadModule(ThreadModule()) //
			.repositoryModule(RepositoryModule()) //
			.cryptorsModule(CryptorsModule(appCryptors)) //
			.build()
	}

	private fun cleanupCache() {
		CacheCleanupTask(applicationComponent.fileUtil()).execute()
	}

	private fun setupLoggingFramework() {
		if (BuildConfig.DEBUG) {
			Timber.plant(DebugLogger())
		}
		Timber.plant(ReleaseLogger(Companion.applicationContext))
	}

	/**
	 * Provides the application's Dagger dependency injection component.
	 *
	 * @return The initialized ApplicationComponent for resolving app-wide dependencies.
	 */
	override fun getComponent(): ApplicationComponent {
		return applicationComponent
	}

	private val startedActivities = AtomicInteger(0)
	private val serviceNotifier: ActivityLifecycleCallbacks = object : NoOpActivityLifecycleCallbacks() {
		/**
		 * Tracks app foreground state when an activity starts and notifies bound services.
		 *
		 * Increments the internal started-activity counter, updates the Cryptors service with whether
		 * the app is in the foreground, and — when transitioning from background to foreground on
		 * freemium builds — triggers a purchase refresh to detect refunds or lapsed subscriptions.
		 *
		 * @param activity The activity that has started.
		 */
		override fun onActivityStarted(activity: Activity) {
			// Using onActivityStarted/Stopped (not Resumed/Paused) because B.onStart fires before A.onStop during
			// intra-app navigation, so the counter never transiently hits 0 on screen transitions.
			val newCount = startedActivities.incrementAndGet()
			updateService(newCount)
			if (newCount == 1 && FlavorConfig.isFreemiumFlavor) {
				// Refresh purchases on background→foreground so a refund or lapsed subscription is detected.
				// The coordinator persists revoke state via SharedPreferences; PurchaseRevokedToastObserver picks it up
				// on the next activity resume. Outcome is ignored here because auto-refresh only drives the revoke toast.
				restorePurchases()
			}
		}

		/**
		 * Notifies the application that an activity has stopped and updates the app's foreground state.
		 *
		 * Decrements the internal started-activities counter and informs the bound service of the new count so it can adjust foreground/background behavior.
		 */
		override fun onActivityStopped(activity: Activity) {
			updateService(startedActivities.decrementAndGet())
		}
	}

	/**
	 * Ensures the CryptorsService is bound and notifies it whether the application is in the foreground.
	 *
	 * @param startedCount The current count of started activities; the service is considered in foreground when this value is greater than 0.
	 */
	private fun updateService(startedCount: Int = startedActivities.get()) {
		val localServiceBinder = cryptoServiceBinder
		if (localServiceBinder == null) {
			startCryptorsService()
		} else {
			localServiceBinder.appInForeground(startedCount > 0)
		}
	}

	fun allVaultsLocked(): Boolean {
		return appCryptors.isEmpty()
	}

	fun suspendLock() {
		val localServiceBinder = cryptoServiceBinder
		localServiceBinder?.suspendLock()
	}

	fun unSuspendLock() {
		val localServiceBinder = cryptoServiceBinder
		localServiceBinder?.unSuspendLock()
	}

	companion object {

		private lateinit var applicationContext: Context
		fun applicationContext(): Context {
			return applicationContext
		}
	}

	init {
		Companion.applicationContext = this
	}
}
