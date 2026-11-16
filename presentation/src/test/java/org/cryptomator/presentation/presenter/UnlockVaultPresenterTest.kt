package org.cryptomator.presentation.presenter

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.common.base.Optional
import org.cryptomator.cryptolib.api.InvalidPassphraseException
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.hub.HubVaultOperationNotSupportedException
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.ResumeVaultUploadsUseCase
import org.cryptomator.domain.usecases.vault.ChangePasswordUseCase
import org.cryptomator.domain.usecases.vault.CreateHubDeviceUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultUseCase
import org.cryptomator.domain.usecases.vault.GetRecoveryKeyUseCase
import org.cryptomator.domain.usecases.vault.GetUnverifiedVaultConfigUseCase
import org.cryptomator.domain.usecases.vault.LockVaultUseCase
import org.cryptomator.domain.usecases.vault.PrepareUnlockUseCase
import org.cryptomator.domain.usecases.vault.RemoveStoredVaultPasswordsAndDisableBiometricAuthUseCase
import org.cryptomator.domain.usecases.vault.SaveVaultUseCase
import org.cryptomator.domain.usecases.vault.UnlockHubVaultUseCase
import org.cryptomator.domain.usecases.vault.UnlockVaultUsingMasterkeyUseCase
import org.cryptomator.domain.usecases.vault.UpdateVaultParameterIfChangedRemotelyUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.ui.activity.view.UnlockVaultView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI

class UnlockVaultPresenterTest {

	private val getUnverifiedVaultConfigUseCase = mock<GetUnverifiedVaultConfigUseCase>()
	private val getUnverifiedVaultConfigLauncher = mock<GetUnverifiedVaultConfigUseCase.Launcher>(defaultAnswer = Mockito.RETURNS_SELF)
	private val getRecoveryKeyUseCase = mock<GetRecoveryKeyUseCase>()
	private val getRecoveryKeyLauncher = mock<GetRecoveryKeyUseCase.Launcher>(defaultAnswer = Mockito.RETURNS_SELF)
	private val exceptionMappings = mock<ExceptionHandlers>()
	private val authenticationExceptionHandler = mock<AuthenticationExceptionHandler>()
	private val masterkeyConfig = UnverifiedVaultConfig("jwt", URI("masterkeyfile:masterkey.cryptomator"), 8)
	private val activity = mock<Activity>()
	private val view = mock<UnlockVaultView>()
	private val intent = mock<UnlockVaultIntent>()
	private val vault = mock<Vault>()
	private val vaultModel = mock<VaultModel>()
	private val hubConfig = mock<UnverifiedHubVaultConfig>()

	private lateinit var intents: MockedConstruction<Intent>

	private lateinit var inTest: UnlockVaultPresenter

	@BeforeEach
	fun setup() {
		whenever(view.activity()).thenReturn(activity)
		whenever(view.context()).thenReturn(mock<Context>())
		whenever(intent.vaultAction()).thenReturn(UnlockVaultIntent.VaultAction.UNLOCK)
		whenever(intent.vaultModel()).thenReturn(vaultModel)
		whenever(vaultModel.toVault()).thenReturn(vault)
		whenever(getUnverifiedVaultConfigUseCase.withVault(any())).thenReturn(getUnverifiedVaultConfigLauncher)
		whenever(getRecoveryKeyUseCase.withVault(any())).thenReturn(getRecoveryKeyLauncher)
		intents = Mockito.mockConstruction(Intent::class.java, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF))

		inTest = presenter()
	}

	@AfterEach
	fun tearDown() {
		intents.close()
	}

	@Test
	fun testShowRecoveryKeyAsksForThePasswordOfAMasterkeyVaultOnce() {
		retrieveVaultConfigToShowRecoveryKey().onSuccess(Optional.of(masterkeyConfig))

		verify(view).showRecoveryKeyPasswordDialog(vaultModel, masterkeyConfig)

		inTest.pause()
		inTest.resume()

		verify(view, times(1)).showRecoveryKeyPasswordDialog(vaultModel, masterkeyConfig)
	}

	@Test
	fun testShowRecoveryKeyAsksForThePasswordOfALegacyVault() {
		retrieveVaultConfigToShowRecoveryKey().onSuccess(Optional.absent())

		verify(view).showRecoveryKeyPasswordDialog(vaultModel, null)
	}

	@Test
	fun testVaultConfigRetrievedWhilePausedAsksForThePasswordOnResume() {
		val vaultConfigRetrieved = retrieveVaultConfigToShowRecoveryKey()

		inTest.pause()
		vaultConfigRetrieved.onSuccess(Optional.of(masterkeyConfig))

		verify(view, never()).showRecoveryKeyPasswordDialog(any(), anyOrNull())

		inTest.resume()
		inTest.pause()
		inTest.resume()

		verify(view, times(1)).showRecoveryKeyPasswordDialog(vaultModel, masterkeyConfig)
	}

	@Test
	fun testShowRecoveryKeyIsNotSupportedForAHubVault() {
		whenever(intent.vaultAction()).thenReturn(UnlockVaultIntent.VaultAction.SHOW_RECOVERY_KEY)

		inTest.allowedHubHosts(hubConfig, vault)

		verify(exceptionMappings).handle(eq(view), any<HubVaultOperationNotSupportedException>())
		verify(view).finish()
	}

	@Test
	fun testCorrectPasswordShowsTheRecoveryKeyOnce() {
		requestRecoveryKey(masterkeyConfig, "password").onSuccess("recovery key")

		verify(getRecoveryKeyLauncher).andUnverifiedVaultConfig(Optional.of(masterkeyConfig))
		verify(getRecoveryKeyLauncher).andPassword("password")
		verify(view).showRecoveryKeyDialog(vaultModel, "recovery key")

		inTest.pause()
		inTest.resume()

		verify(view, times(1)).showRecoveryKeyDialog(vaultModel, "recovery key")
	}

	@Test
	fun testRecoveryKeyRetrievedWhilePausedIsShownOnResume() {
		val recoveryKeyRetrieved = requestRecoveryKey(null, "password")

		inTest.pause()
		recoveryKeyRetrieved.onSuccess("recovery key")

		verify(view, never()).showRecoveryKeyDialog(any(), any())

		inTest.resume()
		inTest.pause()
		inTest.resume()

		verify(view, times(1)).showRecoveryKeyDialog(vaultModel, "recovery key")
	}

	@Test
	fun testExpiredCloudAuthenticationIsLeftToTheAuthenticationHandler() {
		whenever(authenticationExceptionHandler.handleAuthenticationException(any(), any(), any())).thenReturn(true)
		val authenticationExpired = RuntimeException("authentication expired")

		requestRecoveryKey(null, "password").onError(authenticationExpired)

		verify(authenticationExceptionHandler).handleAuthenticationException(eq(inTest), eq(authenticationExpired), any())
		verify(exceptionMappings, never()).handle(any(), any())
	}

	@Test
	fun testWrongPasswordShowsTheErrorAndKeepsAskingForThePassword() {
		val wrongPassword = InvalidPassphraseException()

		requestRecoveryKey(null, "wrong").onError(wrongPassword)

		verify(exceptionMappings).handle(view, wrongPassword)
		verify(view, never()).showRecoveryKeyDialog(any(), any())
		verify(view, never()).finish()
	}

	@Test
	fun testRecoveryKeyIsRequestedAgainWithTheReauthenticatedCloud() {
		val cloud = mock<Cloud>()
		whenever(cloud.type()).thenReturn(CloudType.WEBDAV)
		val cloudModel = mock<CloudModel>()
		whenever(cloudModel.toCloud()).thenReturn(cloud)
		val authenticated = mock<ActivityResult>()
		whenever(authenticated.isResultOk).thenReturn(true)
		whenever(authenticated.getSingleResult(CloudModel::class.java)).thenReturn(cloudModel)
		val storedVault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.WEBDAV).withPosition(0).build()

		inTest.showRecoveryKeyAfterAuthentication(authenticated, storedVault, masterkeyConfig, "password")

		val requestedFor = argumentCaptor<Vault>()
		verify(getRecoveryKeyUseCase).withVault(requestedFor.capture())
		assertSame(cloud, requestedFor.firstValue.cloud)
		verify(getRecoveryKeyLauncher).andUnverifiedVaultConfig(Optional.of(masterkeyConfig))
		verify(getRecoveryKeyLauncher).andPassword("password")
		verify(getRecoveryKeyLauncher).run(any())
	}

	@Test
	fun testFailedReauthenticationEndsShowingTheRecoveryKey() {
		val failure = RuntimeException("authentication failed")
		val notAuthenticated = mock<ActivityResult>()
		whenever(notAuthenticated.isResultOk).thenReturn(false)
		whenever(notAuthenticated.getSingleResult(Throwable::class.java)).thenReturn(failure)

		inTest.showRecoveryKeyAfterAuthentication(notAuthenticated, vault, null, "password")

		verify(view).closeDialog()
		verify(exceptionMappings).handle(view, failure)
		verify(view).finish()
		verify(getRecoveryKeyUseCase, never()).withVault(any())
	}

	private fun retrieveVaultConfigToShowRecoveryKey(): ResultHandler<Optional<UnverifiedVaultConfig>> {
		whenever(intent.vaultAction()).thenReturn(UnlockVaultIntent.VaultAction.SHOW_RECOVERY_KEY)
		inTest.setup()
		val vaultConfigRetrieved = argumentCaptor<ResultHandler<Optional<UnverifiedVaultConfig>>>()
		verify(getUnverifiedVaultConfigLauncher).run(vaultConfigRetrieved.capture())
		return vaultConfigRetrieved.firstValue
	}

	private fun requestRecoveryKey(unverifiedVaultConfig: UnverifiedVaultConfig?, password: String): ResultHandler<String> {
		inTest.onShowRecoveryKeyClick(vaultModel, unverifiedVaultConfig, password)
		val recoveryKeyRetrieved = argumentCaptor<ResultHandler<String>>()
		verify(getRecoveryKeyLauncher).run(recoveryKeyRetrieved.capture())
		return recoveryKeyRetrieved.firstValue
	}

	private fun presenter(): UnlockVaultPresenter {
		return UnlockVaultPresenter(
			mock<ChangePasswordUseCase>(),
			mock<DeleteVaultUseCase>(),
			getRecoveryKeyUseCase,
			getUnverifiedVaultConfigUseCase,
			mock<LockVaultUseCase>(),
			mock<UnlockVaultUsingMasterkeyUseCase>(),
			mock<PrepareUnlockUseCase>(),
			mock<UnlockHubVaultUseCase>(),
			mock<CreateHubDeviceUseCase>(),
			mock<RemoveStoredVaultPasswordsAndDisableBiometricAuthUseCase>(),
			mock<SaveVaultUseCase>(),
			mock<UpdateVaultParameterIfChangedRemotelyUseCase>(),
			mock<ResumeVaultUploadsUseCase>(),
			authenticationExceptionHandler,
			mock<SharedPreferencesHandler>(),
			exceptionMappings
		).also {
			it.view = view
			it.intent = intent
		}
	}
}
