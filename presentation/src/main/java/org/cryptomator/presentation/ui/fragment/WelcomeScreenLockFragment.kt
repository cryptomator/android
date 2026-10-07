package org.cryptomator.presentation.ui.fragment

import android.content.Context
import android.view.View
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentWelcomeScreenLockBinding

@Fragment
class WelcomeScreenLockFragment : BaseFragment<FragmentWelcomeScreenLockBinding>(FragmentWelcomeScreenLockBinding::inflate) {

	interface Listener {
		fun onSetScreenLock(setScreenLock: Boolean)
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
		binding.btnSetScreenLock.setOnClickListener {
			listener?.onSetScreenLock(binding.cbSetScreenLock.isChecked)
		}
	}

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
