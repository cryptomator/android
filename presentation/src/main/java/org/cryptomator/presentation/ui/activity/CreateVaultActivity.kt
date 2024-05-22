package org.cryptomator.presentation.ui.activity

import android.view.inputmethod.EditorInfo
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityCreateVaultBinding
import org.cryptomator.presentation.presenter.CreateVaultPresenter
import org.cryptomator.presentation.ui.activity.view.CreateVaultView
import javax.inject.Inject

@Activity
class CreateVaultActivity : BaseActivity<ActivityCreateVaultBinding>(ActivityCreateVaultBinding::inflate), CreateVaultView {

	@Inject
	lateinit var createVaultPresenter: CreateVaultPresenter

	override fun setupView() {
		binding.llContentCreateVault.createVaultButton.setOnClickListener {
			createVaultPresenter.onCreateVaultClicked(binding.llContentCreateVault.vaultNameEditText.text.toString())
		}
		binding.llContentCreateVault.createVaultButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				createVaultPresenter.onCreateVaultClicked(binding.llContentCreateVault.vaultNameEditText.text.toString())
			}
			false
		}
		setupToolbar()
		binding.llContentCreateVault.vaultNameEditText.requestFocus()
	}

	private fun setupToolbar() {
		binding.mtToolbar.toolbar.setTitle(R.string.screen_enter_vault_name_title)
		setSupportActionBar(binding.mtToolbar.toolbar)
	}

}
