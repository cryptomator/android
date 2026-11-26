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
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.S3Provider
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToS3UseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.S3AddOrChangeView
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.crypto.CredentialCryptor
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.Serializable

class S3AddOrChangePresenterTest {

	private val cloud = S3Cloud.aS3Cloud() //
		.withAccessKey("encryptedAccessKey") //
		.withSecretKey("encryptedSecretKey") //
		.withS3Bucket("vaults") //
		.withS3Endpoint("http://192.168.178.20:9000") //
		.withS3Region("eu-central-1") //
		.withDisplayName("MinIO") //
		.build()
	private val addOrChangeCloudConnectionUseCase = mock<AddOrChangeCloudConnectionUseCase>()
	private val connectToS3UseCase = mock<ConnectToS3UseCase>()
	private val connectToS3Launcher = mock<ConnectToS3UseCase.Launcher>()
	private val exceptionHandlers = mock<ExceptionHandlers>()
	private val activity = mock<Activity>()
	private val view = mock<S3AddOrChangeView>()
	private val context = mock<Context>()
	private val shownToast = mock<Toast>()
	private var shownToastText: CharSequence? = null

	private lateinit var inTest: S3AddOrChangePresenter

	@BeforeEach
	fun setup() {
		whenever(view.activity()).thenReturn(activity)
		whenever(view.context()).thenReturn(context)
		whenever(context.getString(any())).thenAnswer { "message ${it.arguments[0]}" }
		whenever(connectToS3UseCase.withCloud(any())).thenReturn(connectToS3Launcher)
		val addOrChangeCloudConnectionLauncher = mock<AddOrChangeCloudConnectionUseCase.Launcher>()
		whenever(addOrChangeCloudConnectionUseCase.withCloud(any())).thenReturn(addOrChangeCloudConnectionLauncher)
		inTest = S3AddOrChangePresenter(addOrChangeCloudConnectionUseCase, connectToS3UseCase, exceptionHandlers)
		inTest.view = view
	}

	@Test
	fun testMissingLocalNetworkPermissionIsRequested() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)

				connectResultHandler().onError(LocalNetworkPermissionMissingException(cloud))

				activityCompat.verify { ActivityCompat.requestPermissions(any(), eq(arrayOf<String?>(Manifest.permission.ACCESS_LOCAL_NETWORK)), any()) }
				verify(exceptionHandlers, never()).handle(any(), any())
			}
		}
	}

	@Test
	fun testOtherErrorsAreShown() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)
				val error = WrongCredentialsException(cloud)

				connectResultHandler().onError(error)

				verify(exceptionHandlers).handle(view, error)
				activityCompat.verify({ ActivityCompat.requestPermissions(any(), any(), any()) }, never())
			}
		}
	}

	@Test
	fun testAuthenticatedConnectionIsSavedAndReturnedToTheCaller() {
		Mockito.mockConstruction(Intent::class.java).use { intents ->
			connectResultHandler().onSuccess(null)

			val savedCloud = argumentCaptor<Cloud>()
			verify(addOrChangeCloudConnectionUseCase).withCloud(savedCloud.capture())
			assertThat((savedCloud.firstValue as S3Cloud).s3Bucket(), `is`("vaults"))
			val result = intents.constructed().single()
			verify(result).putExtra(CloudConnectionListPresenter.SELECTED_CLOUD, savedCloud.firstValue as Serializable)
			verify(activity).setResult(Activity.RESULT_OK, result)
			verify(view).finish()
		}
	}

	@Test
	fun testGrantedLocalNetworkPermissionRetriesConnecting() {
		inTest.s3LocalNetworkPermissionResult(PermissionsResult(mock(), true), cloud)

		verify(connectToS3UseCase).withCloud(cloud)
		verify(connectToS3Launcher).run(any())
	}

	@Test
	fun testDeniedLocalNetworkPermissionDoesntRetry() {
		inTest.s3LocalNetworkPermissionResult(PermissionsResult(mock(), false), cloud)

		verify(connectToS3UseCase, never()).withCloud(any())
	}

	@Test
	fun testAuthenticatedCloudKeepsTheProvider() {
		inTest.setS3Provider(S3Provider.WASABI)

		inTest.authenticate("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://s3.eu-central-1.wasabisys.com", "eu-central-1", null, "Wasabi")

		verify(connectToS3UseCase).withCloud(argThat { provider() == S3Provider.WASABI })
	}

	@Test
	fun testAuthenticatedCloudHasNoProviderByDefault() {
		inTest.authenticate("encryptedAccessKey", "encryptedSecretKey", "vaults", "http://192.168.178.20:9000", "eu-central-1", null, "MinIO")

		verify(connectToS3UseCase).withCloud(argThat { provider() == null })
	}

	@Test
	fun testProviderInputWithListedRegionIsCheckedWithTheEndpointOfThatRegion() {
		inTest.setS3Provider(S3Provider.WASABI)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", S3Provider.WASABI.choices.indexOf("eu-central-1"), "", "Wasabi") }

		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://s3.eu-central-1.wasabisys.com", "eu-central-1", null, "Wasabi")
	}

	@Test
	fun testProviderInputWithListedRegionIgnoresTextLeftInTheOtherField() {
		inTest.setS3Provider(S3Provider.WASABI)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", 0, "eu-west-9", "Wasabi") }

		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://s3.us-east-1.wasabisys.com", "us-east-1", null, "Wasabi")
	}

	@Test
	fun testProviderInputWithOtherRegionIsCheckedWithTheEndpointOfTheTypedRegion() {
		inTest.setS3Provider(S3Provider.WASABI)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", OTHER_WASABI_REGION, "eu-west-9", "Wasabi") }

		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://s3.eu-west-9.wasabisys.com", "eu-west-9", null, "Wasabi")
	}

	@Test
	fun testProviderInputWithAccountIdIsCheckedWithTheEndpointOfTheAccountAndTheFixedRegion() {
		inTest.setS3Provider(S3Provider.CLOUDFLARE_R2)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "0123456789abcdef", null, "", "R2") }

		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://0123456789abcdef.r2.cloudflarestorage.com", "auto", null, "R2")
	}

	@Test
	fun testProviderInputWithEndpointNameIsCheckedWithTheEndpointOfThatNameAndTheFixedRegion() {
		inTest.setS3Provider(S3Provider.VULTR)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", S3Provider.VULTR.choices.indexOf("ewr1"), "", "Vultr") }

		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://ewr1.vultrobjects.com", "us-east-1", null, "Vultr")
	}

	@Test
	fun testProviderInputWithServerIsCheckedWithTheServerAsEndpoint() {
		inTest.setS3Provider(S3Provider.MINIO)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "http://192.168.178.20:9000", "", 0, "", "MinIO") }

		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "http://192.168.178.20:9000", "us-east-1", null, "MinIO")
	}

	@Test
	fun testProviderInputWithServerWithoutSchemeInUpperCaseIsNormalized() {
		inTest.setS3Provider(S3Provider.MINIO)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "MinIO.Example.org:9000/", "", OTHER_MINIO_REGION, "Home_1", "MinIO") }

		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://minio.example.org:9000", "Home_1", null, "MinIO")
	}

	@ParameterizedTest
	@EnumSource(S3Provider::class)
	fun testEveryListedChoiceOfAProviderIsAccepted(provider: S3Provider) {
		inTest.setS3Provider(provider)
		val selections = provider.choices.indices.toList().ifEmpty { listOf(null) }

		checkingProviderInput {
			selections.forEach { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "s3.example.org", "account", it, "", "name") }
		}

		verify(view, times(selections.size)).onCheckUserInputSucceeded(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any())
		verify(shownToast, never()).show()
	}

	@Test
	fun testProviderInputWithOtherRegionOfShortestAndLongestLengthIsAccepted() {
		inTest.setS3Provider(S3Provider.MINIO)

		checkingProviderInput {
			inTest.checkProviderInput("accessKey", "secretKey", "vaults", "minio.example.org", "", OTHER_MINIO_REGION, "a", "MinIO")
			inTest.checkProviderInput("accessKey", "secretKey", "vaults", "minio.example.org", "", OTHER_MINIO_REGION, "a".repeat(63), "MinIO")
		}

		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://minio.example.org", "a", null, "MinIO")
		verify(view).onCheckUserInputSucceeded("encryptedAccessKey", "encryptedSecretKey", "vaults", "https://minio.example.org", "a".repeat(63), null, "MinIO")
	}

	@Test
	fun testProviderInputWithTooLongOtherRegionIsRejected() {
		inTest.setS3Provider(S3Provider.MINIO)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "minio.example.org", "", OTHER_MINIO_REGION, "a".repeat(64), "MinIO") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_region_is_invalid)
	}

	@Test
	fun testProviderInputWithoutRequiredServerIsRejected() {
		inTest.setS3Provider(S3Provider.MINIO)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", 0, "", "MinIO") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_server_not_empty)
	}

	@Test
	fun testProviderInputWithoutRequiredAccountIdIsRejected() {
		inTest.setS3Provider(S3Provider.CLOUDFLARE_R2)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", null, "", "R2") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_account_id_not_empty)
	}

	@Test
	fun testProviderInputWithoutChosenRegionIsRejected() {
		inTest.setS3Provider(S3Provider.WASABI)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", null, "", "Wasabi") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_region_not_empty)
	}

	@Test
	fun testProviderInputWithOtherRegionLeftEmptyIsRejected() {
		inTest.setS3Provider(S3Provider.WASABI)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", OTHER_WASABI_REGION, "", "Wasabi") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_region_not_empty)
	}

	@Test
	fun testProviderInputWithoutChosenEndpointNameIsRejected() {
		inTest.setS3Provider(S3Provider.VULTR)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", null, "", "Vultr") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_endpoint_name_not_empty)
	}

	@ParameterizedTest
	@ValueSource(strings = ["minio example.org", "https://", "minio.example.org:abc", "minio.example.org/minio", "https://minio.example.org/#files", "minio.example.org?a=b", "alice:secret@minio.example.org"])
	fun testProviderInputWithInvalidServerIsRejected(server: String) {
		inTest.setS3Provider(S3Provider.MINIO)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", server, "", 0, "", "MinIO") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_server_is_invalid)
	}

	@ParameterizedTest
	@ValueSource(strings = ["eu central 1", "eu-central-1/", "evil.example.org/", "evil.example.org#", "evil.example.org?", "alice@eu-central-1", "eu-central-1:80", "EU-CENTRAL-1", "eu-central-1.wasabisys.com", "-eu-central-1", "eu-central-1-", "_eu"])
	fun testProviderInputWithInvalidOtherRegionIsRejected(region: String) {
		inTest.setS3Provider(S3Provider.WASABI)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", OTHER_WASABI_REGION, region, "Wasabi") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_region_is_invalid)
	}

	@ParameterizedTest
	@ValueSource(strings = ["eu west", "minio.example.org", "eu/west", "-home", "home_"])
	fun testProviderInputWithInvalidOtherRegionOfAServerIsRejected(region: String) {
		inTest.setS3Provider(S3Provider.MINIO)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "minio.example.org", "", OTHER_MINIO_REGION, region, "MinIO") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_region_is_invalid)
	}

	@ParameterizedTest
	@ValueSource(strings = ["ewr 1", "evil.example.org/", "evil.example.org#", "alice@ewr1", "EWR1"])
	fun testProviderInputWithInvalidOtherEndpointNameIsRejected(endpointName: String) {
		inTest.setS3Provider(S3Provider.VULTR)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", "", S3Provider.VULTR.choices.size, endpointName, "Vultr") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_endpoint_name_is_invalid)
	}

	@ParameterizedTest
	@ValueSource(strings = ["0123 4567", "evil.example.org/", "evil.example.org#", "alice@0123456789abcdef"])
	fun testProviderInputWithInvalidAccountIdIsRejected(accountId: String) {
		inTest.setS3Provider(S3Provider.CLOUDFLARE_R2)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "vaults", "", accountId, null, "", "R2") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_account_id_is_invalid)
	}

	@Test
	fun testProviderInputWithoutBucketIsRejected() {
		inTest.setS3Provider(S3Provider.WASABI)

		checkingProviderInput { inTest.checkProviderInput("accessKey", "secretKey", "", "", "", 0, "", "Wasabi") }

		verifyInputRejectedWith(R.string.screen_s3_settings_msg_bucket_not_empty)
	}

	private fun checkingProviderInput(check: () -> Unit) {
		val credentialCryptor = mock<CredentialCryptor>()
		whenever(credentialCryptor.encrypt("accessKey")).thenReturn("encryptedAccessKey")
		whenever(credentialCryptor.encrypt("secretKey")).thenReturn("encryptedSecretKey")
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
		verify(view, never()).onCheckUserInputSucceeded(any(), any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), any())
	}

	private fun connectResultHandler(): ResultHandler<Void> {
		inTest.authenticate("encryptedAccessKey", "encryptedSecretKey", "vaults", "http://192.168.178.20:9000", "eu-central-1", null, "MinIO")
		val handler = argumentCaptor<ResultHandler<Void>>()
		verify(connectToS3Launcher).run(handler.capture())
		return handler.firstValue
	}

	companion object {

		private val OTHER_WASABI_REGION = S3Provider.WASABI.choices.size
		private val OTHER_MINIO_REGION = S3Provider.MINIO.choices.size
	}
}
