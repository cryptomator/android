package org.cryptomator.presentation.presenter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToSmbUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.SmbAddOrChangeView
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.crypto.CredentialCryptor
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.Serializable
import java.net.UnknownHostException

class SmbAddOrChangePresenterTest {

	private val addOrChangeCloudConnectionUseCase = mock<AddOrChangeCloudConnectionUseCase>()
	private val addOrChangeCloudConnectionLauncher = mock<AddOrChangeCloudConnectionUseCase.Launcher>()
	private val connectToSmbUseCase = mock<ConnectToSmbUseCase>()
	private val connectToSmbLauncher = mock<ConnectToSmbUseCase.Launcher>()
	private val exceptionHandlers = mock<ExceptionHandlers>()
	private val view = mock<SmbAddOrChangeView>()

	private lateinit var inTest: SmbAddOrChangePresenter

	@BeforeEach
	fun setup() {
		val context = mock<Context>()
		whenever(context.getString(any())).thenReturn("message")
		whenever(view.context()).thenReturn(context)
		whenever(view.activity()).thenReturn(mock())
		whenever(addOrChangeCloudConnectionUseCase.withCloud(any())).thenReturn(addOrChangeCloudConnectionLauncher)
		whenever(connectToSmbUseCase.withCloud(any())).thenReturn(connectToSmbLauncher)
		inTest = SmbAddOrChangePresenter(addOrChangeCloudConnectionUseCase, connectToSmbUseCase, exceptionHandlers)
		inTest.view = view
	}

	@ParameterizedTest
	@CsvSource(
		"smb://nas/share/, smb://nas/share",
		"\\\\nas\\share\\folder, smb://nas/share/folder",
		"nas:1445/share, smb://nas:1445/share"
	)
	fun testValidInputIsNormalized(url: String, expected: String) {
		Mockito.mockStatic(CredentialCryptor::class.java).use { cryptors ->
			val cryptor = mock<CredentialCryptor>()
			cryptors.`when`<CredentialCryptor> { CredentialCryptor.getInstance(any()) }.thenReturn(cryptor)
			whenever(cryptor.encrypt("secret")).thenReturn("encryptedSecret")

			inTest.checkUserInput(url, "alice", "secret", 42L)

			verify(view).onCheckUserInputSucceeded(expected, "alice", "encryptedSecret", 42L)
		}
	}

	@ParameterizedTest
	@ValueSource(strings = ["", "smb://", "smb://nas", "smb://nas:abc/share"])
	fun testInvalidUrlIsRejected(url: String) {
		Mockito.mockStatic(Toast::class.java).use { toasts ->
			toasts.`when`<Toast> { Toast.makeText(any<Context>(), any<CharSequence>(), any()) }.thenReturn(mock())

			inTest.checkUserInput(url, "alice", "secret", null)

			verify(view, never()).onCheckUserInputSucceeded(any(), any(), any(), anyOrNull())
		}
	}

	@Test
	fun testMissingPasswordIsRejected() {
		Mockito.mockStatic(Toast::class.java).use { toasts ->
			toasts.`when`<Toast> { Toast.makeText(any<Context>(), any<CharSequence>(), any()) }.thenReturn(mock())

			inTest.checkUserInput("smb://nas/share", "alice", "", null)

			verify(view, never()).onCheckUserInputSucceeded(any(), any(), any(), anyOrNull())
		}
	}

	@Test
	fun testAuthenticatedConnectionIsSaved() {
		connectResultHandler().onSuccess(null)

		val savedCloud = argumentCaptor<Cloud>()
		verify(addOrChangeCloudConnectionUseCase).withCloud(savedCloud.capture())
		assertThat(savedCloud.firstValue.id(), `is`(42L))
	}

	@Test
	fun testSavedConnectionIsReturnedToTheCaller() {
		val activity = mock<Activity>()
		whenever(view.activity()).thenReturn(activity)
		connectResultHandler().onSuccess(null)
		val savedCloud = argumentCaptor<Cloud>()
		verify(addOrChangeCloudConnectionUseCase).withCloud(savedCloud.capture())
		val saveHandler = argumentCaptor<ResultHandler<Void>>()
		verify(addOrChangeCloudConnectionLauncher).run(saveHandler.capture())

		Mockito.mockConstruction(Intent::class.java).use { intents ->
			saveHandler.firstValue.onSuccess(null)

			val result = intents.constructed().single()
			verify(result).putExtra(CloudConnectionListPresenter.SELECTED_CLOUD, savedCloud.firstValue as Serializable)
			verify(activity).setResult(Activity.RESULT_OK, result)
			verify(view).finish()
		}
	}

	@Test
	fun testUnknownHostShowsServerNotFound() {
		connectResultHandler().onError(NetworkConnectionException(UnknownHostException("nas")))

		verify(view).showError(R.string.error_server_not_found)
		verify(addOrChangeCloudConnectionUseCase, never()).withCloud(any())
	}

	@Test
	fun testOtherErrorsAreHandledByExceptionHandlers() {
		val error = NetworkConnectionException()

		connectResultHandler().onError(error)

		verify(exceptionHandlers).handle(view, error)
		verify(addOrChangeCloudConnectionUseCase, never()).withCloud(any())
	}

	@Test
	fun testMissingLocalNetworkPermissionIsRequested() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)

				connectResultHandler().onError(LocalNetworkPermissionMissingException(SmbCloud.aSmbCloud().build()))

				activityCompat.verify { ActivityCompat.requestPermissions(any(), eq(arrayOf<String?>(Manifest.permission.ACCESS_LOCAL_NETWORK)), any()) }
				verify(exceptionHandlers, never()).handle(any(), any())
			}
		}
	}

	@Test
	fun testGrantedLocalNetworkPermissionRetriesConnecting() {
		val cloud = SmbCloud.aSmbCloud().withUrl("smb://nas/share").withUsername("alice").withPassword("encryptedSecret").build()

		inTest.smbLocalNetworkPermissionResult(PermissionsResult(mock(), true), cloud)

		verify(connectToSmbUseCase).withCloud(cloud)
		verify(connectToSmbLauncher).run(any())
	}

	@Test
	fun testDeniedLocalNetworkPermissionDoesntRetry() {
		inTest.smbLocalNetworkPermissionResult(PermissionsResult(mock(), false), SmbCloud.aSmbCloud().build())

		verify(connectToSmbUseCase, never()).withCloud(any())
	}

	private fun connectResultHandler(): ResultHandler<Void> {
		inTest.authenticate("smb://nas/share", "alice", "encryptedSecret", 42L)
		val handler = argumentCaptor<ResultHandler<Void>>()
		verify(connectToSmbLauncher).run(handler.capture())
		return handler.firstValue
	}
}
