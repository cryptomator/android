package org.cryptomator.presentation.workflow

import android.content.Context
import android.content.Intent
import org.cryptomator.domain.Vault
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.GetRootFolderUseCase
import org.cryptomator.domain.usecases.vault.CreateVaultUseCase
import org.cryptomator.domain.usecases.vault.CreatedVault
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.presenter.VaultListPresenter
import org.cryptomator.presentation.ui.activity.view.VaultListView
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CreateNewVaultWorkflowTest {

	private val createVaultUseCase = mock<CreateVaultUseCase>()
	private val createVaultLauncher = mock<CreateVaultUseCase.Launcher>(defaultAnswer = Mockito.RETURNS_SELF)
	private val presenter = mock<VaultListPresenter>()
	private val view = mock<VaultListView>()

	private lateinit var inTest: CreateNewVaultWorkflow

	@BeforeEach
	fun setup() {
		whenever(createVaultUseCase.withVaultName(anyOrNull())).thenReturn(createVaultLauncher)
		whenever(presenter.view).thenReturn(view)
		inTest = CreateNewVaultWorkflow(mock<Context>(), createVaultUseCase, mock<GetRootFolderUseCase>(), mock<CloudModelMapper>(), mock<AuthenticationExceptionHandler>())
		inTest.setup(presenter, mock<Intent>())
	}

	@Test
	fun testCreatedVaultIsHandedOverWithoutCompletingTheProgressAfterwards() {
		val createdVault = CreatedVault(mock<Vault>(), "recovery key")

		val vaultCreation = startVaultCreation()
		vaultCreation.onSuccess(createdVault)
		vaultCreation.onFinished()

		verify(presenter).onCreateVaultCompleted(createdVault)
		verify(view, never()).showProgress(ProgressModel.COMPLETED)
	}

	@Test
	fun testFailedVaultCreationCompletesTheProgress() {
		val failure = RuntimeException("failed")

		val vaultCreation = startVaultCreation()
		vaultCreation.onError(failure)
		vaultCreation.onFinished()

		verify(view).showProgress(ProgressModel.COMPLETED)
		verify(presenter).showError(failure)
		verify(presenter, never()).onCreateVaultCompleted(any())
	}

	private fun startVaultCreation(): ResultHandler<CreatedVault> {
		inTest.completed()

		val vaultCreation = argumentCaptor<ResultHandler<CreatedVault>>()
		inOrder(view, createVaultLauncher) {
			verify(view).showProgress(ProgressModel.GENERIC)
			verify(createVaultLauncher).run(vaultCreation.capture())
		}
		return vaultCreation.firstValue
	}
}
