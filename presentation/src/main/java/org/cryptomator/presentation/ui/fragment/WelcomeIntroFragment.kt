package org.cryptomator.presentation.ui.fragment

import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentWelcomeIntroBinding

@Fragment
class WelcomeIntroFragment : BaseFragment<FragmentWelcomeIntroBinding>(FragmentWelcomeIntroBinding::inflate) {

	/**
	 * Performs any view setup required by the fragment.
	 *
	 * This fragment uses only static content and therefore requires no runtime view initialization.
	 */
	override fun setupView() {
		// static content only
	}
}
