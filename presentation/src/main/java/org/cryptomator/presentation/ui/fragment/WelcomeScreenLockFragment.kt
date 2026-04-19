package org.cryptomator.presentation.ui.fragment

import android.content.Context
import android.view.View
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentWelcomeScreenLockBinding

@Fragment
class WelcomeScreenLockFragment : BaseFragment<FragmentWelcomeScreenLockBinding>(FragmentWelcomeScreenLockBinding::inflate) {

	interface Listener {
		/**
 * Notifies the host that the user requested to enable or disable the screen lock.
 *
 * @param setScreenLock `true` to request enabling the screen lock, `false` to request disabling it.
 */
fun onSetScreenLock(setScreenLock: Boolean)
	}

	private var listener: Listener? = null

	/**
	 * Attaches the fragment to the given context and captures the host `Listener` if the context implements it.
	 *
	 * @param context The Context being attached; if it implements [Listener], it will be stored in the fragment's `listener` property.
	 */
	override fun onAttach(context: Context) {
		super.onAttach(context)
		listener = context as? Listener
	}

	/**
	 * Initializes the fragment's UI elements and listeners.
	 */
	override fun setupView() {
		setupUi()
	}

	/**
	 * Installs the click behavior for the "Set screen lock" button so that tapping it notifies the fragment's
	 * listener with the current checked state of the screen-lock checkbox.
	 */
	private fun setupUi() {
		binding.btnSetScreenLock.setOnClickListener {
			listener?.onSetScreenLock(binding.cbSetScreenLock.isChecked)
		}
	}

	/**
	 * Updates the fragment UI to reflect whether the device has a secure screen lock configured.
	 *
	 * When `isSecure` is true, the "set screen lock" controls are disabled, the checkbox is unchecked,
	 * and the status text is shown; when false, the controls are enabled and the status text is hidden.
	 *
	 * @param isSecure `true` if the device currently has a secure screen lock configured, `false` otherwise.
	 */
	fun updateScreenLockState(isSecure: Boolean) {
		if (!isAdded) {
			return
		}
		binding.btnSetScreenLock.isEnabled = !isSecure
		binding.cbSetScreenLock.isEnabled = !isSecure
		if (isSecure) {
			binding.cbSetScreenLock.isChecked = false
		}
		binding.tvScreenLockStatus.visibility = if (isSecure) View.VISIBLE else View.GONE
	}
}
