package org.cryptomator.presentation.ui.fragment

import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.UnlockVaultPresenter
import javax.inject.Inject

@Fragment(R.layout.fragment_unlock_vault)
class UnlockVaultFragment : BaseFragment() {

	@Inject
	lateinit var presenter: UnlockVaultPresenter

	override fun setupView() {
		presenter.setup()
	}
}
