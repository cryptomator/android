package org.cryptomator.presentation.ui.fragment

import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentWelcomeIntroBinding

@Fragment
class WelcomeIntroFragment : BaseFragment<FragmentWelcomeIntroBinding>(FragmentWelcomeIntroBinding::inflate) {

	override fun setupView() {
		// static content only
	}
}
