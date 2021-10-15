package org.cryptomator.presentation.ui.fragment

import android.view.inputmethod.EditorInfo
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.SetPasswordPresenter
import org.cryptomator.presentation.util.PasswordStrengthUtil
import javax.inject.Inject
import kotlinx.android.synthetic.main.fragment_set_password.createVaultButton
import kotlinx.android.synthetic.main.fragment_set_password.passwordEditText
import kotlinx.android.synthetic.main.fragment_set_password.passwordRetypedEditText
import kotlinx.android.synthetic.main.view_password_strength_indicator.progressBarPwStrengthIndicator
import kotlinx.android.synthetic.main.view_password_strength_indicator.textViewPwStrengthIndicator

@Fragment(R.layout.fragment_set_password)
class SetPasswordFragment : BaseFragment() {

	@Inject
	lateinit var setPasswordPresenter: SetPasswordPresenter

	@Inject
	lateinit var passwordStrengthUtil: PasswordStrengthUtil

	override fun setupView() {
		createVaultButton.setOnClickListener { validatePasswords() }
		createVaultButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				validatePasswords()
			}
			false
		}
		passwordStrengthUtil.startUpdatingPasswordStrengthMeter(
			passwordEditText, //
			progressBarPwStrengthIndicator, //
			textViewPwStrengthIndicator, //
			createVaultButton
		)

		passwordEditText.requestFocus()
	}

	private fun validatePasswords() {
		val password = passwordEditText.text.toString()
		val passwordRetyped = passwordRetypedEditText.text.toString()
		setPasswordPresenter.validatePasswords(password, passwordRetyped)
	}
}
