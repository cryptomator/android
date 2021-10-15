package org.cryptomator.presentation.presenter

import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.SetPasswordView
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.Workflow
import javax.inject.Inject

@PerView
class SetPasswordPresenter @Inject constructor( //
	private val createNewVaultWorkflow: CreateNewVaultWorkflow, //
	exceptionMappings: ExceptionHandlers
) : Presenter<SetPasswordView>(exceptionMappings) {

	override fun workflows(): Iterable<Workflow<*>> {
		return setOf(createNewVaultWorkflow)
	}

	fun validatePasswords(password: String, passwordRetyped: String) {
		if (valid(password, passwordRetyped)) {
			finishWithResult(password)
		}
	}

	private fun valid(password: String, passwordRetyped: String): Boolean {
		if (password.isEmpty()) {
			view?.showMessage(R.string.screen_set_password_msg_password_empty)
			return false
		} else if (password != passwordRetyped) {
			view?.showMessage(R.string.screen_set_password_msg_password_mismatch)
			return false
		}
		return true
	}
}
