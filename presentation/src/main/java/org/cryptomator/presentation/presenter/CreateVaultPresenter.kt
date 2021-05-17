package org.cryptomator.presentation.presenter

import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.CreateVaultView
import org.cryptomator.presentation.util.FileNameValidator.Companion.isInvalidName
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.Workflow
import javax.inject.Inject

@PerView
class CreateVaultPresenter @Inject constructor( //
	private val createNewVaultWorkflow: CreateNewVaultWorkflow, //
	exceptionMappings: ExceptionHandlers
) : Presenter<CreateVaultView>(exceptionMappings) {

	override fun workflows(): Iterable<Workflow<*>> {
		return setOf(createNewVaultWorkflow)
	}

	fun onCreateVaultClicked(vaultName: String) {
		when {
			vaultName.isEmpty() -> {
				view?.showMessage(R.string.screen_enter_vault_name_msg_name_empty)
			}
			isInvalidName(vaultName) -> {
				view?.showMessage(R.string.error_vault_name_contains_invalid_characters)
			}
			else -> {
				finishWithResult(vaultName)
			}
		}
	}
}
