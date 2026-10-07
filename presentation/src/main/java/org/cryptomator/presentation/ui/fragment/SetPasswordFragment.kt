package org.cryptomator.presentation.ui.fragment

import android.view.inputmethod.EditorInfo
import org.cryptomator.generator.Fragment
import org.cryptomator.presentation.databinding.FragmentSetPasswordBinding
import org.cryptomator.presentation.presenter.SetPasswordPresenter
import org.cryptomator.presentation.util.PasswordStrengthUtil
import javax.inject.Inject

@Fragment
class SetPasswordFragment : BaseFragment<FragmentSetPasswordBinding>(FragmentSetPasswordBinding::inflate) {

	@Inject
	lateinit var setPasswordPresenter: SetPasswordPresenter

	@Inject
	lateinit var passwordStrengthUtil: PasswordStrengthUtil

	override fun setupView() {
		binding.createVaultButton.setOnClickListener { validatePasswords() }
		binding.createVaultButton.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_DONE) {
				validatePasswords()
			}
			false
		}
		passwordStrengthUtil.startUpdatingPasswordStrengthMeter(
			binding.passwordEditText, //
			binding.llPasswordStrengthIndicator.pbPasswordStrengthIndicator, //
			binding.llPasswordStrengthIndicator.tvPwStrengthIndicator, //
			binding.createVaultButton
		)

		binding.passwordEditText.requestFocus()
	}

	private fun validatePasswords() {
		val password = binding.passwordEditText.text.toString()
		val passwordRetyped = binding.passwordRetypedEditText.text.toString()
		setPasswordPresenter.validatePasswords(password, passwordRetyped)
	}
}
