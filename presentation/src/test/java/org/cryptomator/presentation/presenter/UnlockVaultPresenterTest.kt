package org.cryptomator.presentation.presenter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.google.common.base.Optional
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.browser.BrowserDescriptor
import org.cryptomator.cryptolib.api.InvalidPassphraseException
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.UnverifiedHubVaultConfig
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.hub.HubAuthenticationFailedException
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
import org.cryptomator.presentation.util.HubAuthRedirects
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.util.SharedPreferencesHandler
import org.json.JSONObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedConstruction
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.URI

class UnlockVaultPresenterTest {

	private val unlockHubVaultUseCase = mock<UnlockHubVaultUseCase>()
	private val unlockHubVaultLauncher = mock<UnlockHubVaultUseCase.Launcher>(defaultAnswer = Mockito.RETURNS_SELF)
	private val getUnverifiedVaultConfigUseCase = mock<GetUnverifiedVaultConfigUseCase>()
	private val getUnverifiedVaultConfigLauncher = mock<GetUnverifiedVaultConfigUseCase.Launcher>(defaultAnswer = Mockito.RETURNS_SELF)
	private val getRecoveryKeyUseCase = mock<GetRecoveryKeyUseCase>()
	private val getRecoveryKeyLauncher = mock<GetRecoveryKeyUseCase.Launcher>(defaultAnswer = Mockito.RETURNS_SELF)
	private val createHubDeviceUseCase = mock<CreateHubDeviceUseCase>()
	private val createHubDeviceLauncher = mock<CreateHubDeviceUseCase.Launcher>(defaultAnswer = Mockito.RETURNS_SELF)
	private val updateVaultParameterIfChangedRemotelyUseCase = mock<UpdateVaultParameterIfChangedRemotelyUseCase>()
	private val updateVaultParameterIfChangedRemotelyLauncher = mock<UpdateVaultParameterIfChangedRemotelyUseCase.Launcher>(defaultAnswer = Mockito.RETURNS_SELF)
	private val hubAuthRedirects = HubAuthRedirects()
	private val exceptionMappings = mock<ExceptionHandlers>()
	private val authenticationExceptionHandler = mock<AuthenticationExceptionHandler>()
	private val masterkeyConfig = UnverifiedVaultConfig("jwt", URI("masterkeyfile:masterkey.cryptomator"), 8)
	private val activity = mock<Activity>()
	private val view = mock<UnlockVaultView>()
	private val intent = mock<UnlockVaultIntent>()
	private val vault = mock<Vault>()
	private val vaultModel = mock<VaultModel>()
	private val hubConfig = mock<UnverifiedHubVaultConfig>()
	private val authRequest = mock<AuthorizationRequest>()
	private val authRequestUri = mock<Uri>()
	private val redirectUri = mock<Uri>()
	private var browser: BrowserDescriptor? = BrowserDescriptor("org.example.browser", emptySet(), "1", false)
	private var createdAuthService: (AuthorizationService) -> Unit = {}

	private lateinit var uris: MockedStatic<Uri>
	private lateinit var authRequests: MockedStatic<AuthorizationRequest>
	private lateinit var authRequestBuilders: MockedConstruction<AuthorizationRequest.Builder>
	private lateinit var authServices: MockedConstruction<AuthorizationService>
	private lateinit var authResponseBuilders: MockedConstruction<AuthorizationResponse.Builder>
	private lateinit var intents: MockedConstruction<Intent>
	private lateinit var jsonObjects: MockedConstruction<JSONObject>

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
		whenever(unlockHubVaultUseCase.withVault(any())).thenReturn(unlockHubVaultLauncher)
		whenever(createHubDeviceUseCase.withAccessToken(any())).thenReturn(createHubDeviceLauncher)
		whenever(updateVaultParameterIfChangedRemotelyUseCase.withVault(any())).thenReturn(updateVaultParameterIfChangedRemotelyLauncher)
		whenever(hubConfig.authEndpoint).thenReturn(URI("https://hub.example.org/auth"))
		whenever(hubConfig.tokenEndpoint).thenReturn(URI("https://hub.example.org/token"))
		whenever(hubConfig.clientId).thenReturn("cryptomator")
		whenever(authRequest.toUri()).thenReturn(authRequestUri)
		whenever(authRequest.jsonSerializeString()).thenReturn("opened-request")
		setField(authRequest, "state", "expected-state")

		uris = Mockito.mockStatic(Uri::class.java)
		uris.`when`<Uri> { Uri.parse(any()) }.thenReturn(mock<Uri>())
		authRequests = Mockito.mockStatic(AuthorizationRequest::class.java)
		authRequests.`when`<AuthorizationRequest> { AuthorizationRequest.jsonDeserialize("opened-request") }.thenReturn(authRequest)
		authRequestBuilders = Mockito.mockConstruction(AuthorizationRequest.Builder::class.java) { builder, _ ->
			whenever(builder.build()).thenReturn(authRequest)
		}
		authServices = Mockito.mockConstruction(AuthorizationService::class.java) { service, _ ->
			whenever(service.browserDescriptor).thenAnswer { browser }
			createdAuthService(service)
		}
		authResponseBuilders = Mockito.mockConstruction(AuthorizationResponse.Builder::class.java) { builder, _ ->
			val response = mock<AuthorizationResponse>()
			whenever(response.createTokenExchangeRequest()).thenReturn(mock<TokenRequest>())
			whenever(builder.fromUri(any())).thenReturn(builder)
			whenever(builder.build()).thenReturn(response)
		}
		intents = Mockito.mockConstruction(Intent::class.java, Mockito.withSettings().defaultAnswer(Mockito.RETURNS_SELF))
		jsonObjects = Mockito.mockConstruction(JSONObject::class.java)

		inTest = presenter()
	}

	@AfterEach
	fun tearDown() {
		listOf(uris, authRequests, authRequestBuilders, authServices, authResponseBuilders, intents, jsonObjects).forEach { it.close() }
	}

	@Test
	fun testRedirectOfTheOpenedLoginBringsTheUnlockBackAndUnlocks() {
		inTest.allowedHubHosts(hubConfig, vault)
		val requestCode = argumentCaptor<Int>()
		val login = intents.constructed().single()
		verify(activity).startActivityForResult(eq(login), requestCode.capture())
		verify(login).setPackage("org.example.browser")
		verify(login).setData(authRequestUri)

		receiveRedirect("expected-state")
		val relaunch = intents.constructed().last()
		verify(relaunch).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
		verify(activity).startActivity(relaunch)

		inTest.onActivityResult(requestCode.firstValue, Activity.RESULT_CANCELED, null)
		completeTokenRequest(accessToken("access-token"))

		verify(authResponseBuilders.constructed().single()).fromUri(redirectUri)
		verify(unlockHubVaultLauncher).andAccessToken("access-token")
		verify(unlockHubVaultLauncher).run(any())
		verify(view, never()).finish()
	}

	@Test
	fun testUnlockedHubVaultIsStoredAsItWasUnlockedAndNotAsTheVaultListKnewIt() {
		val unlocked = mock<Vault>()
		val unlockedCloud = mock<CryptoCloud> { on { vault } doReturn unlocked }
		inTest.allowedHubHosts(hubConfig, vault)
		val requestCode = argumentCaptor<Int>()
		verify(activity).startActivityForResult(any(), requestCode.capture())
		receiveRedirect("expected-state")
		inTest.onActivityResult(requestCode.firstValue, Activity.RESULT_CANCELED, null)
		completeTokenRequest(accessToken("access-token"))
		val vaultUnlocked = argumentCaptor<ResultHandler<Cloud>>()
		verify(unlockHubVaultLauncher).run(vaultUnlocked.capture())

		vaultUnlocked.firstValue.onSuccess(unlockedCloud)

		verify(updateVaultParameterIfChangedRemotelyUseCase).withVault(unlocked)
		verify(updateVaultParameterIfChangedRemotelyLauncher).run(any())
	}

	@Test
	fun testRecreatedUnlockContinuesWithTheOpenedLoginInsteadOfOpeningASecondOne() {
		inTest.allowedHubHosts(hubConfig, vault)
		val requestCode = argumentCaptor<Int>()
		verify(activity).startActivityForResult(any(), requestCode.capture())

		val recreated = recreated(inTest)
		recreated.setup()
		verify(getUnverifiedVaultConfigUseCase, never()).withVault(any())

		receiveRedirect("expected-state")
		recreated.onActivityResult(requestCode.firstValue, Activity.RESULT_CANCELED, null)
		completeTokenRequest(accessToken("access-token"))
		verify(activity, times(1)).startActivityForResult(anyOrNull(), any())
		verify(unlockHubVaultLauncher).andAccessToken("access-token")
	}

	@Test
	fun testRecreatedUnlockStartsOverOnceTheLoginResultWasHandled() {
		inTest.allowedHubHosts(hubConfig, vault)
		val requestCode = argumentCaptor<Int>()
		verify(activity).startActivityForResult(any(), requestCode.capture())
		receiveRedirect("expected-state")
		inTest.onActivityResult(requestCode.firstValue, Activity.RESULT_CANCELED, null)

		val recreated = recreated(inTest)
		recreated.setup()

		verify(getUnverifiedVaultConfigUseCase).withVault(vault)
		verify(getUnverifiedVaultConfigLauncher).run(any())
	}

	@Test
	fun testDestroyedUnlockIsNotBroughtBackByALateRedirect() {
		inTest.allowedHubHosts(hubConfig, vault)

		inTest.destroy()
		receiveRedirect("expected-state")

		verify(activity, never()).startActivity(anyOrNull())
	}

	@Test
	fun testHubLoginOpensInACustomTabOfTheBrowser() {
		browser = BrowserDescriptor("org.example.browser", emptySet(), "1", true)
		val customTab = mock<Intent>(defaultAnswer = Mockito.RETURNS_SELF)
		val customTabsIntent = mock<CustomTabsIntent>().also { setField(it, "intent", customTab) }
		val customTabsIntentBuilder = mock<CustomTabsIntent.Builder>()
		whenever(customTabsIntentBuilder.build()).thenReturn(customTabsIntent)
		createdAuthService = { whenever(it.createCustomTabsIntentBuilder()).thenReturn(customTabsIntentBuilder) }

		inTest.allowedHubHosts(hubConfig, vault)

		verify(customTab).setPackage("org.example.browser")
		verify(customTab).setData(authRequestUri)
		verify(activity).startActivityForResult(eq(customTab), any())
	}

	@Test
	fun testFinishingUnlockIsNotBroughtBackByALateRedirect() {
		inTest.allowedHubHosts(hubConfig, vault)

		whenever(activity.isFinishing).thenReturn(true)
		receiveRedirect("expected-state")

		verify(activity, never()).startActivity(anyOrNull())
	}

	@Test
	fun testMissingBrowserFailsInsteadOfOpeningAnyApp() {
		browser = null

		inTest.allowedHubHosts(hubConfig, vault)

		verify(activity, never()).startActivityForResult(anyOrNull(), any())
		assertFailed()
	}

	@Test
	fun testMissingRedirectFailsTheUnlock() {
		hubAuthenticationResult()

		assertFailedWithoutTokenRequest()
	}

	@Test
	fun testErrorRedirectFailsTheUnlock() {
		whenever(redirectUri.getQueryParameter("error")).thenReturn("access_denied")
		receiveRedirect("expected-state")

		hubAuthenticationResult()

		assertFailedWithoutTokenRequest()
	}

	@Test
	fun testOpaqueRedirectFailsTheUnlock() {
		whenever(redirectUri.isOpaque).thenReturn(true)
		whenever(redirectUri.getQueryParameter(any())).thenThrow(UnsupportedOperationException("This isn't a hierarchical URI."))
		hubAuthRedirects.receiveRedirect(redirectUri)

		hubAuthenticationResult()

		assertFailedWithoutTokenRequest()
	}

	@Test
	fun testRedirectOfAnotherLoginFailsTheUnlock() {
		receiveRedirect("forged-state")

		hubAuthenticationResult()

		assertFailedWithoutTokenRequest()
	}

	@Test
	fun testFailedTokenExchangeFailsTheUnlock() {
		receiveRedirect("expected-state")

		hubAuthenticationResult()
		completeTokenRequest(null, mock<AuthorizationException>())

		verify(unlockHubVaultUseCase, never()).withVault(any())
		assertFailed()
	}

	@Test
	fun testRedirectOfTheDeviceRegistrationLoginCreatesTheDevice() {
		inTest.onCreateHubDeviceClick(vaultModel, hubConfig, "Pixel", "setup-code")
		val requestCode = argumentCaptor<Int>()
		verify(activity).startActivityForResult(any(), requestCode.capture())

		receiveRedirect("expected-state")
		inTest.onActivityResult(requestCode.firstValue, Activity.RESULT_CANCELED, null)
		completeTokenRequest(accessToken("access-token"))

		verify(createHubDeviceUseCase).withAccessToken("access-token")
		verify(createHubDeviceLauncher).andUnverifiedVaultConfig(hubConfig)
		verify(createHubDeviceLauncher).andDeviceName("Pixel")
		verify(createHubDeviceLauncher).andSetupCode("setup-code")
		verify(createHubDeviceLauncher).run(any())
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

	private fun receiveRedirect(state: String) {
		whenever(redirectUri.getQueryParameter("state")).thenReturn(state)
		hubAuthRedirects.receiveRedirect(redirectUri)
	}

	private fun hubAuthenticationResult() {
		inTest.hubAuthenticationUnlock(ActivityResult(mock(), null, false), vault, hubConfig, "opened-request")
	}

	private fun completeTokenRequest(token: TokenResponse?, error: AuthorizationException? = null) {
		val callback = argumentCaptor<AuthorizationService.TokenResponseCallback>()
		verify(authServices.constructed().last()).performTokenRequest(any(), callback.capture())
		callback.firstValue.onTokenRequestCompleted(token, error)
	}

	private fun accessToken(value: String): TokenResponse {
		return mock<TokenResponse>().also { setField(it, "accessToken", value) }
	}

	private fun assertFailedWithoutTokenRequest() {
		authServices.constructed().forEach { verify(it, never()).performTokenRequest(any(), any<AuthorizationService.TokenResponseCallback>()) }
		assertFailed()
	}

	private fun assertFailed() {
		verify(exceptionMappings).handle(eq(view), any<HubAuthenticationFailedException>())
		verify(view).finish()
	}

	private fun setField(target: Any, name: String, value: Any) {
		target.javaClass.getField(name).also { it.isAccessible = true }.set(target, value)
	}

	private fun recreated(presenter: UnlockVaultPresenter): UnlockVaultPresenter {
		val state = instanceStateBundle()
		InstanceStates.save(presenter, state)
		presenter.destroy()
		return presenter().also { InstanceStates.restore(it, state) }
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
			unlockHubVaultUseCase,
			createHubDeviceUseCase,
			mock<RemoveStoredVaultPasswordsAndDisableBiometricAuthUseCase>(),
			mock<SaveVaultUseCase>(),
			updateVaultParameterIfChangedRemotelyUseCase,
			mock<ResumeVaultUploadsUseCase>(),
			authenticationExceptionHandler,
			mock<SharedPreferencesHandler>(),
			hubAuthRedirects,
			exceptionMappings
		).also {
			it.view = view
			it.intent = intent
		}
	}
}
