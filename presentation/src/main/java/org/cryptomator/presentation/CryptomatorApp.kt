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
import org.cryptomator.presentation.service.PendingCallbackQueue
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

	private val pendingProductDetailsCallbacks = PendingCallbackQueue<List<ProductInfo>>()

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

	fun launchPurchaseFlow(activity: WeakReference<Activity>, productId: String) {
		if (FlavorConfig.isFreemiumFlavor) {
			iapBillingServiceBinder?.startPurchaseFlow(activity, productId)
		}
	}

	fun queryProductDetails(callback: (List<ProductInfo>) -> Unit) {
		if (!FlavorConfig.isFreemiumFlavor) {
			callback(emptyList())
			return
		}
		iapBillingServiceBinder?.queryProductDetails(callback) ?: pendingProductDetailsCallbacks.enqueue(callback)
	}

	private fun drainPendingProductDetailsCallbacks() {
		val snapshot = pendingProductDetailsCallbacks.drainSnapshot() ?: return
		iapBillingServiceBinder?.queryProductDetails { products ->
			snapshot.forEach { it(products) }
		}
	}

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

	override fun getComponent(): ApplicationComponent {
		return applicationComponent
	}

	private val startedActivities = AtomicInteger(0)
	private val serviceNotifier: ActivityLifecycleCallbacks = object : NoOpActivityLifecycleCallbacks() {
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

		override fun onActivityStopped(activity: Activity) {
			updateService(startedActivities.decrementAndGet())
		}
	}

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
