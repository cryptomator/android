package org.cryptomator.presentation.presenter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.exception.authentication.WebDavCertificateUntrustedAuthenticationException
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToWebDavUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.WebDavAddOrChangeView
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.crypto.CredentialCryptor
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WebDavAddOrChangePresenterTest {

	private val cloud = WebDavCloud.aWebDavCloudCloud().withUrl("https://192.168.178.20/webdav").withUsername("alice").withPassword("encryptedSecret").build()
	private val connectToWebDavUseCase = mock<ConnectToWebDavUseCase>()
	private val connectToWebDavLauncher = mock<ConnectToWebDavUseCase.Launcher>()
	private val authenticationExceptionHandler = mock<AuthenticationExceptionHandler>()
	private val exceptionHandlers = mock<ExceptionHandlers>()
	private val view = mock<WebDavAddOrChangeView>()
	private val context = mock<Context>()
	private val shownToast = mock<Toast>()
	private var shownToastText: CharSequence? = null

	private lateinit var inTest: WebDavAddOrChangePresenter

	@BeforeEach
	fun setup() {
		whenever(view.activity()).thenReturn(mock())
		whenever(view.context()).thenReturn(context)
		whenever(context.getString(any())).thenAnswer { "message ${it.arguments[0]}" }
		whenever(connectToWebDavUseCase.withCloud(any())).thenReturn(connectToWebDavLauncher)
		inTest = WebDavAddOrChangePresenter(mock<AddOrChangeCloudConnectionUseCase>(), connectToWebDavUseCase, authenticationExceptionHandler, exceptionHandlers)
		inTest.view = view
	}

	@Test
	fun testMissingLocalNetworkPermissionIsRequested() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)

				connectResultHandler().onError(LocalNetworkPermissionMissingException(cloud))

				activityCompat.verify { ActivityCompat.requestPermissions(any(), eq(arrayOf<String?>(Manifest.permission.ACCESS_LOCAL_NETWORK)), any()) }
				verify(authenticationExceptionHandler, never()).handleAuthenticationException(any(), any(), any())
				verify(exceptionHandlers, never()).handle(any(), any())
			}
		}
	}

	@Test
	fun testUntrustedCertificateIsHandledAsAnAuthenticationProblem() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)
				val error = WebDavCertificateUntrustedAuthenticationException(cloud, "certificate")

				connectResultHandler().onError(error)

				verify(authenticationExceptionHandler).handleAuthenticationException(any(), eq(error), any())
				activityCompat.verify({ ActivityCompat.requestPermissions(any(), any(), any()) }, never())
			}
		}
	}

	@Test
	fun testGrantedLocalNetworkPermissionRetriesConnecting() {
		inTest.webDavLocalNetworkPermissionResult(PermissionsResult(mock(), true), cloud)

		verify(connectToWebDavUseCase).withCloud(cloud)
		verify(connectToWebDavLauncher).run(any())
	}

	@Test
	fun testDeniedLocalNetworkPermissionDoesntRetry() {
		inTest.webDavLocalNetworkPermissionResult(PermissionsResult(mock(), false), cloud)

		verify(connectToWebDavUseCase, never()).withCloud(any())
	}

	@Test
	fun testAuthenticatedCloudKeepsTheProvider() {
		inTest.setWebDavProvider(WebDavProvider.KOOFR)

		inTest.authenticate("alice", "encryptedSecret", "https://app.koofr.net/dav/Koofr", null, null)

		verify(connectToWebDavUseCase).withCloud(argThat { provider() == WebDavProvider.KOOFR })
	}

	@Test
	fun testAuthenticatedCloudHasNoProviderByDefault() {
		inTest.authenticate("alice", "encryptedSecret", "https://192.168.178.20/webdav", null, null)

		verify(connectToWebDavUseCase).withCloud(argThat { provider() == null })
	}

	@Test
	fun testProviderInputIsCheckedWithTheProvidersUrl() {
		inTest.setWebDavProvider(WebDavProvider.NEXTCLOUD)

		checkingProviderInput { inTest.checkProviderInput("cloud.example.org", "", "alice@example.org", "secret") }

		verify(view).onCheckUserInputSucceeded("https://cloud.example.org/remote.php/webdav", "alice@example.org", "encryptedSecret", null, null)
	}

	@Test
	fun testProviderInputWithSpaceIdIsCheckedWithTheSpacesUrl() {
		inTest.setWebDavProvider(WebDavProvider.OPENCLOUD)

		checkingProviderInput { inTest.checkProviderInput("cloud.example.org", "space-1", "alice", "secret") }

		verify(view).onCheckUserInputSucceeded("https://cloud.example.org/remote.php/dav/spaces/space-1", "alice", "encryptedSecret", null, null)
	}

	@Test
	fun testProviderInputWithHttpServerAsksBeforeUsingHttp() {
		inTest.setWebDavProvider(WebDavProvider.SEAFILE)

		checkingProviderInput { inTest.checkProviderInput("http://192.168.178.20:8082", "", "alice", "secret") }

		verify(view).showAskForHttpDialog("http://192.168.178.20:8082/seafdav", "alice", "encryptedSecret", null, null)
		verify(view, never()).onCheckUserInputSucceeded(any(), any(), any(), anyOrNull(), anyOrNull())
	}

	@Test
	fun testProviderInputWithoutRequiredServerIsRejected() {
		inTest.setWebDavProvider(WebDavProvider.NEXTCLOUD)

		checkingProviderInput { inTest.checkProviderInput("", "", "alice", "secret") }

		verifyInputRejectedWith(R.string.screen_webdav_settings_msg_server_must_not_be_empty)
	}

	@Test
	fun testProviderInputWithoutRequiredSpaceIdIsRejected() {
		inTest.setWebDavProvider(WebDavProvider.OPENCLOUD)

		checkingProviderInput { inTest.checkProviderInput("cloud.example.org", "", "alice", "secret") }

		verifyInputRejectedWith(R.string.screen_webdav_settings_msg_space_id_must_not_be_empty)
	}

	@Test
	fun testProviderInputWithServerSchemeAndHostInUpperCaseIsNormalized() {
		inTest.setWebDavProvider(WebDavProvider.STACK)

		checkingProviderInput { inTest.checkProviderInput("HTTPS://Knowledgebase.Stack.Storage/", "", "felix", "secret") }

		verify(view).onCheckUserInputSucceeded("https://knowledgebase.stack.storage/remote.php/webdav", "felix", "encryptedSecret", null, null)
		verify(view, never()).showAskForHttpDialog(any(), any(), any(), anyOrNull(), anyOrNull())
	}

	@Test
	fun testProviderInputWithSpaceInHttpServerPathAsksWithAnEncodedUrl() {
		inTest.setWebDavProvider(WebDavProvider.SEAFILE)

		checkingProviderInput { inTest.checkProviderInput("http://nas.local/my cloud", "", "alice", "secret") }

		verify(view).showAskForHttpDialog("http://nas.local/my%20cloud/seafdav", "alice", "encryptedSecret", null, null)
	}

	@Test
	fun testProviderInputWithBracketsInHttpServerPathAsksBeforeUsingHttp() {
		inTest.setWebDavProvider(WebDavProvider.SEAFILE)

		checkingProviderInput { inTest.checkProviderInput("http://nas.local/files[1]", "", "alice", "secret") }

		verify(view).showAskForHttpDialog("http://nas.local/files[1]/seafdav", "alice", "encryptedSecret", null, null)
	}

	@ParameterizedTest
	@ValueSource(strings = ["knowledgebase stack.storage", "https://", "http://", "cloud.example.org:abc", "https://cloud.example.org/#files", "https://cloud.example.org/?a=b", "cloud.example.org?a=b", "https://felix:secret@cloud.example.org", "felix@cloud.example.org", ":secret@cloud.example.org"])
	fun testProviderInputWithInvalidServerIsRejected(server: String) {
		inTest.setWebDavProvider(WebDavProvider.STACK)

		checkingProviderInput { inTest.checkProviderInput(server, "", "felix", "secret") }

		verifyInputRejectedWith(R.string.screen_webdav_settings_msg_server_is_invalid)
	}

	@Test
	fun testProviderInputWithoutUsernameIsRejected() {
		inTest.setWebDavProvider(WebDavProvider.KOOFR)

		checkingProviderInput { inTest.checkProviderInput("", "", "", "secret") }

		verifyInputRejectedWith(R.string.screen_webdav_settings_msg_username_must_not_be_empty)
	}

	private fun checkingProviderInput(check: () -> Unit) {
		val credentialCryptor = mock<CredentialCryptor>()
		whenever(credentialCryptor.encrypt("secret")).thenReturn("encryptedSecret")
		Mockito.mockStatic(Toast::class.java).use { toast ->
			Mockito.mockStatic(CredentialCryptor::class.java).use { cryptor ->
				toast.`when`<Toast> { Toast.makeText(anyOrNull(), any<CharSequence>(), any()) }.thenAnswer {
					shownToastText = it.getArgument<CharSequence>(1)
					shownToast
				}
				cryptor.`when`<CredentialCryptor> { CredentialCryptor.getInstance(anyOrNull()) }.thenReturn(credentialCryptor)
				check()
			}
		}
	}

	private fun verifyInputRejectedWith(message: Int) {
		assertThat(shownToastText, `is`<CharSequence>("message $message"))
		verify(shownToast).show()
		verify(view, never()).onCheckUserInputSucceeded(any(), any(), any(), anyOrNull(), anyOrNull())
		verify(view, never()).showAskForHttpDialog(any(), any(), any(), anyOrNull(), anyOrNull())
	}

	private fun connectResultHandler(): ResultHandler<Void> {
		inTest.authenticate("alice", "encryptedSecret", "https://192.168.178.20/webdav", null, null)
		val handler = argumentCaptor<ResultHandler<Void>>()
		verify(connectToWebDavLauncher).run(handler.capture())
		return handler.firstValue
	}
}
