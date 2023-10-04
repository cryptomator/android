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
import org.cryptomator.presentation.service.AutoUploadNotification
import org.cryptomator.presentation.service.AutoUploadService
import org.cryptomator.presentation.service.CryptorsService
import org.cryptomator.presentation.shared.SharedCreation
import org.cryptomator.util.NoOpActivityLifecycleCallbacks
import org.cryptomator.util.SharedPreferencesHandler
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

	override fun onCreate() {
		super.onCreate()
		SharedCreation.onCreate()
		@Suppress("KotlinConstantConditions") //
		val flavor = when (BuildConfig.FLAVOR) {
			"apkstore" -> "APK Store Edition"
			"fdroid" -> "F-Droid Edition"
			"lite" -> "F-Droid Main Repo Edition"
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
		AppCompatDelegate.setDefaultNightMode(SharedPreferencesHandler(applicationContext()).screenStyleMode)
		cleanupCache()

		if (SharedPreferencesHandler(applicationContext()).microsoftWorkaround()) {
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

	private fun startAutoUploadService() {
		bindService(Intent(this, AutoUploadService::class.java), object : ServiceConnection {
			override fun onServiceConnected(name: ComponentName, service: IBinder) {
				Timber.tag("App").i("Auto upload service connected")
				autoUploadServiceBinder = service as AutoUploadService.Binder
				autoUploadServiceBinder?.init( //
					applicationComponent.cloudContentRepository(),  //
					applicationComponent.fileUtil(),  //
					applicationComponent.contentResolverUtil(),  //
					_applicationContext
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

	override fun getComponent(): ApplicationComponent {
		return applicationComponent
	}

	private val resumedActivities = AtomicInteger(0)
	private val serviceNotifier: ActivityLifecycleCallbacks = object : NoOpActivityLifecycleCallbacks() {
		override fun onActivityResumed(activity: Activity) {
			updateService(resumedActivities.incrementAndGet())
		}

		override fun onActivityPaused(activity: Activity) {
			updateService(resumedActivities.decrementAndGet())
		}
	}

	private fun updateService(resumedCount: Int = resumedActivities.get()) {
		val localServiceBinder = cryptoServiceBinder
		if (localServiceBinder == null) {
			startCryptorsService()
		} else {
			localServiceBinder.appInForeground(resumedCount > 0)
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

		private lateinit var _applicationContext: Context

		fun applicationContext(): Context {
			return _applicationContext
		}

		fun isApplicationContextInitialized(): Boolean {
			return this::_applicationContext.isInitialized
		}
	}

	init {
		_applicationContext = this
	}
}
