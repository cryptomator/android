package org.cryptomator.presentation.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.snackbar.Snackbar
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.di.HasComponent
import org.cryptomator.presentation.di.component.ActivityComponent
import org.cryptomator.presentation.di.component.ApplicationComponent
import org.cryptomator.presentation.di.component.DaggerActivityComponent
import org.cryptomator.presentation.di.module.ActivityModule
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.presenter.InstanceStates
import org.cryptomator.presentation.presenter.Presenter
import org.cryptomator.presentation.ui.activity.view.View
import org.cryptomator.presentation.ui.dialog.GenericProgressDialog
import org.cryptomator.presentation.ui.snackbar.SnackbarAction
import org.cryptomator.util.SharedPreferencesHandler
import java.lang.String.format
import javax.inject.Inject
import kotlin.reflect.KClass
import timber.log.Timber

abstract class BaseActivity<VB : ViewBinding>(val bindingFactory: (LayoutInflater) -> VB) : AppCompatActivity(), View, ActivityCompat.OnRequestPermissionsResultCallback, HasComponent<ActivityComponent> {

	@Inject
	lateinit var exceptionMappings: ExceptionHandlers

	@Inject
	lateinit var sharedPreferencesHandler: SharedPreferencesHandler

	protected val binding: VB by lazy { bindingFactory(layoutInflater) }

	private var activityComponent: ActivityComponent? = null

	private var presenter: Presenter<*>? = null

	private var currentDialog: DialogFragment? = null
	private var closeDialogOnResume: Boolean = false

	/**
	 * Get the Main Application component for dependency injection.
	 *
	 * @return [org.cryptomator.presentation.di.component.ApplicationComponent]
	 */
	private val applicationComponent: ApplicationComponent
		get() = cryptomatorApp.component

	private val cryptomatorApp: CryptomatorApp
		get() = application as CryptomatorApp

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		logLifecycle("onCreate")

		this.activityComponent = initializeDagger()

		javaClass.getAnnotation(Activity::class.java)?.let {
			setContentView(binding.root)

			Activities.setIntent(this)
			this.presenter = Activities.initializePresenter(this)
			afterIntentInjected()

			if (savedInstanceState != null) {
				restoreState(savedInstanceState)
			}

			setupView()
			setupPresenter()

			if (savedInstanceState == null) {
				createAndAddFragment()
			}

			currentDialog = supportFragmentManager.findFragmentByTag(ACTIVE_DIALOG) as? DialogFragment
			closeDialogOnResume = currentDialog != null
		} ?: Timber.tag("BaseActivity").e("Failed to initialize Activity because config is null")
	}

	override fun onStart() {
		super.onStart()
		logLifecycle("onStart")
	}

	override fun onRestart() {
		super.onRestart()
		logLifecycle("onRestart")
	}

	public override fun onResume() {
		super.onResume()
		logLifecycleAsInfo("onResume")

		// not using android extensions to access activityRootVIew because the view might be from different layouts with different type
		findViewById<android.view.View>(R.id.activity_root_view)?.filterTouchesWhenObscured = sharedPreferencesHandler.disableAppWhenObscured()

		val config = javaClass.getAnnotation(Activity::class.java)
		if (config?.secure == true && sharedPreferencesHandler.secureScreen() && !BuildConfig.DEBUG) {
			window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
		} else {
			window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
		}

		if (closeDialogOnResume) {
			closeDialog()
		}
		closeDialogOnResume = false

		if (cryptomatorApp.allVaultsLocked()) {
			vaultExpectedToBeUnlocked()
		}
	}

	internal open fun vaultExpectedToBeUnlocked() {
	}

	override fun onResumeFragments() {
		super.onResumeFragments()
		logLifecycle("onResumeFragments")
		presenter?.resume()
	}

	public override fun onPause() {
		super.onPause()
		logLifecycle("onPause")
		presenter?.pause()
	}

	override fun onStop() {
		super.onStop()
		logLifecycle("onStop")
	}

	public override fun onDestroy() {
		super.onDestroy()
		logLifecycle("onDestroy")
		presenter?.destroy()
	}

	private fun initializeDagger(): ActivityComponent {
		val activityComponent = DaggerActivityComponent.builder()
			.applicationComponent(applicationComponent)
			.activityModule(ActivityModule(this))
			.build()
		Activities.inject(activityComponent, this)
		return activityComponent
	}

	private fun createAndAddFragment() {
		val fragment = createFragment()
		fragment?.let { addFragment(R.id.fragment_container, it) }
	}

	private fun afterIntentInjected() {

	}

	internal open fun setupView() {

	}

	internal open fun setupPresenter() {

	}

	internal open fun createFragment(): Fragment? = null

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		val menuResource = getCustomMenuResource()
		if (menuResource != NO_MENU) {
			menuInflater.inflate(menuResource, menu)
			return true
		}
		return super.onCreateOptionsMenu(menu)
	}

	open fun getCustomMenuResource(): Int = NO_MENU

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		onMenuItemSelected(item.itemId)
		return super.onOptionsItemSelected(item)
	}

	internal open fun onMenuItemSelected(itemId: Int): Boolean = false

	/**
	 * Adds a [Fragment] to this activity's layout.
	 *
	 * @param containerViewId The container view to whereClause add the fragment.
	 * @param fragment The fragment to be added.
	 */
	private fun addFragment(containerViewId: Int, fragment: Fragment) {
		val fragmentTransaction = this.supportFragmentManager.beginTransaction()
		fragmentTransaction.add(containerViewId, fragment)
		fragmentTransaction.commit()
	}

	@JvmOverloads
	internal fun replaceFragment(fragment: Fragment, fragmentAnimation: FragmentAnimation, addToBackStack: Boolean = true) {
		val transaction = supportFragmentManager.beginTransaction()
		transaction.setCustomAnimations(fragmentAnimation.enter, fragmentAnimation.exit, fragmentAnimation.popEnter, fragmentAnimation.popExit)
		transaction.replace(R.id.fragment_container, fragment)
		if (addToBackStack) {
			transaction.addToBackStack(null)
		}
		transaction.commit()
	}

	override fun getComponent(): ActivityComponent? = activityComponent

	override fun activity(): android.app.Activity = this

	override fun context(): Context = this

	override fun showDialog(dialog: DialogFragment) {
		closeDialog()
		currentDialog = dialog
		dialog.show(supportFragmentManager, ACTIVE_DIALOG)
	}

	override fun isShowingDialog(dialog: KClass<out DialogFragment>): Boolean {
		return if (currentDialog != null) {
			dialog.isInstance(currentDialog)
		} else false
	}

	override fun currentDialog(): DialogFragment? {
		return currentDialog
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		logLifecycle("onNewIntent")
		presenter?.onNewIntent(intent)
	}

	override fun closeDialog() {
		currentDialog?.dismissAllowingStateLoss()
		currentDialog = null
	}

	override fun showMessage(messageId: Int, vararg args: Any) {
		val message = getString(messageId)
		showMessage(message, *args)
	}

	override fun showMessage(message: String, vararg args: Any) {
		val formattedMessage = format(message, *args)
		if (currentDialog is MessageDisplay) {
			(currentDialog as MessageDisplay).showMessage(formattedMessage)
		} else {
			showToastMessage(formattedMessage)
		}
		Timber.tag("Message").i(formattedMessage)
	}

	override fun showProgress(progress: ProgressModel) {
		if (currentDialog is ProgressAware) {
			(currentDialog as ProgressAware).showProgress(progress)
		} else {
			showDialog(GenericProgressDialog.create(progress))
		}
		Timber.tag("Progress").v("%s %d%%", progress.state().name(), progress.progress())
	}

	override fun finish() {
		logLifecycle("finish")
		super.finish()
	}

	override fun showError(messageId: Int) {
		val message = getString(messageId)
		if (currentDialog is ErrorDisplay) {
			(currentDialog as ErrorDisplay).showError(messageId)
		} else {
			showToastMessage(message)
		}
		Timber.tag("Message").w(message)
	}

	override fun showError(message: String) {
		if (currentDialog is ErrorDisplay) {
			(currentDialog as ErrorDisplay).showError(message)
		} else {
			showToastMessage(message)
		}
		Timber.tag("Message").w(message)
	}

	override fun showSnackbar(messageId: Int, action: SnackbarAction) {
		Snackbar.make(snackbarView(), messageId, Snackbar.LENGTH_INDEFINITE).setAction(action.text, action).show()
	}

	internal open fun snackbarView(): android.view.View {
		return activity().findViewById(R.id.locations_recycler_view)
			?: activity().findViewById(R.id.rl_choose_cloud_service)
			?: return activity().findViewById(R.id.coordinator_layout)
	}

	internal fun getCurrentFragment(fragmentContainer: Int): Fragment? = supportFragmentManager.findFragmentById(fragmentContainer)


	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		if (newConfig.orientation == ORIENTATION_LANDSCAPE) {
			logLifecycle("onConfigurationChanged: landscape")
		} else if (newConfig.orientation == ORIENTATION_PORTRAIT) {
			logLifecycle("onConfigurationChanged: portrait")
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		logLifecycle("onSaveInstanceState")
		saveState(outState)
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		logLifecycle("onRestoreInstanceState")
		restoreState(savedInstanceState)
	}

	private fun saveState(state: Bundle) {
		InstanceStates.save(presenter, state)
	}

	private fun restoreState(state: Bundle) {
		InstanceStates.restore(presenter, state)
	}

	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		presenter?.onRequestPermissionsResult(requestCode, permissions, grantResults)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)
		presenter?.onActivityResult(requestCode, resultCode, intent)
	}

	private fun logLifecycle(method: String) {
		Timber.tag("ActivityLifecycle").d("%s %s", method, this)
	}

	private fun logLifecycleAsInfo(method: String) {
		Timber.tag("ActivityLifecycle").i("%s %s", method, this)
	}

	private fun showToastMessage(message: String) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
	}

	internal enum class FragmentAnimation(
		val enter: Int,
		val exit: Int,
		val popEnter: Int,
		val popExit: Int
	) {

		NAVIGATE_IN_TO_FOLDER(R.animator.enter_from_right, R.animator.exit_to_left, R.animator.enter_from_left, R.animator.exit_to_right), //
		NAVIGATE_OUT_OF_FOLDER(R.animator.enter_from_left, R.animator.exit_to_right, R.animator.enter_from_right, R.animator.exit_to_left)
	}

	companion object {

		const val NO_MENU = -1
		private const val ACTIVE_DIALOG = "activeDialog"
	}
}
