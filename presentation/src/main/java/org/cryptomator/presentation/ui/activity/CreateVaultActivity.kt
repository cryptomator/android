package org.cryptomator.presentation.ui.activity

import android.view.inputmethod.EditorInfo
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.CreateVaultPresenter
import org.cryptomator.presentation.ui.activity.view.CreateVaultView
import javax.inject.Inject
import kotlinx.android.synthetic.main.content_create_vault.createVaultButton
import kotlinx.android.synthetic.main.content_create_vault.vaultNameEditText
import kotlinx.android.synthetic.main.toolbar_layout.toolbar

@Activity(layout = R.layout.activity_create_vault)
class CreateVaultActivity : BaseActivity(), CreateVaultView {

	@Inject
	lateinit var createVaultPresenter: CreateVaultPresenter

	override fun setupView() {
		createVaultButton.setOnClickListener {
			createVaultPresenter.onCreateVaultClicked(vaultNameEditText.text.toString())
		}
		createVaultButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				createVaultPresenter.onCreateVaultClicked(vaultNameEditText.text.toString())
			}
			false
		}
		setupToolbar()

		vaultNameEditText.requestFocus()
	}

	private fun setupToolbar() {
		toolbar.setTitle(R.string.screen_enter_vault_name_title)
		setSupportActionBar(toolbar)
	}

}
