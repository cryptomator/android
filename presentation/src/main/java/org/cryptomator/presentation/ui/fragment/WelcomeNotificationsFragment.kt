package org.cryptomator.presentation.ui.fragment

import android.content.Context
import android.view.View
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentWelcomeNotificationsBinding

@Fragment
class WelcomeNotificationsFragment : BaseFragment<FragmentWelcomeNotificationsBinding>(FragmentWelcomeNotificationsBinding::inflate) {

	interface Listener {
		/**
 * Notifies the host that the user has requested notification permission.
 *
 * Implementations should initiate the platform's notification-permission flow or otherwise
 * handle enabling notifications for the app in response to the user's request.
 */
fun onRequestNotifications()
	}

	private var listener: Listener? = null

	/**
	 * Attaches the fragment to its host and assigns the listener if the host implements it.
	 *
	 * If `context` implements `WelcomeNotificationsFragment.Listener`, it is stored in `listener`;
	 * otherwise `listener` remains `null`.
	 *
	 * @param context The host `Context` provided by the system, potentially implementing `Listener`.
	 */
	override fun onAttach(context: Context) {
		super.onAttach(context)
		listener = context as? Listener
	}

	/**
	 * Initializes the fragment's view components.
	 *
	 * Prepares UI wiring and listeners required when the fragment's view is created.
	 */
	override fun setupView() {
		setupUi()
	}

	/**
	 * Wires up the fragment's UI interactions.
	 *
	 * Attaches a click listener to the notification permission button that invokes
	 * the fragment's `Listener.onRequestNotifications()` callback when available.
	 */
	private fun setupUi() {
		binding.btnNotificationPermission.setOnClickListener {
			listener?.onRequestNotifications()
		}
	}

	/**
	 * Update the fragment's notification-permission UI to reflect the current permission state.
	 *
	 * If the fragment is not attached to its host, the call is a no-op.
	 *
	 * When `granted` is true the permission button is disabled and the status text is shown;
	 * when false the button is enabled and the status text is hidden. The permission button
	 * is always made visible.
	 *
	 * @param granted `true` if notification permission is granted, `false` otherwise.
	 */
	fun updatePermissionState(granted: Boolean) {
		if (!isAdded) {
			return
		}
		binding.btnNotificationPermission.isEnabled = !granted
		binding.btnNotificationPermission.visibility = View.VISIBLE
		binding.tvNotificationStatus.visibility = if (granted) View.VISIBLE else View.GONE
	}
}
