package org.cryptomator.presentation.ui.fragment

import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentUnlockVaultBinding
import org.cryptomator.presentation.presenter.UnlockVaultPresenter
import javax.inject.Inject

@Fragment
class UnlockVaultFragment : BaseFragment<FragmentUnlockVaultBinding>(FragmentUnlockVaultBinding::inflate) {

	@Inject
	lateinit var presenter: UnlockVaultPresenter

	override fun setupView() {
		presenter.setup()
	}
}
