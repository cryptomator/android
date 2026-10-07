package org.cryptomator.presentation.ui.fragment

import android.content.Context
import android.view.View
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentWelcomeNotificationsBinding

@Fragment
class WelcomeNotificationsFragment : BaseFragment<FragmentWelcomeNotificationsBinding>(FragmentWelcomeNotificationsBinding::inflate) {

	interface Listener {
		fun onRequestNotifications()
	}

	private var listener: Listener? = null

	override fun onAttach(context: Context) {
		super.onAttach(context)
		listener = context as? Listener
	}

	override fun setupView() {
		setupUi()
	}

	private fun setupUi() {
		binding.btnNotificationPermission.setOnClickListener {
			listener?.onRequestNotifications()
		}
	}

	fun updatePermissionState(granted: Boolean) {
		if (!isAdded) {
			return
		}
		binding.btnNotificationPermission.isEnabled = !granted
		binding.btnNotificationPermission.visibility = View.VISIBLE
		binding.tvNotificationStatus.visibility = if (granted) View.VISIBLE else View.GONE
	}
}
