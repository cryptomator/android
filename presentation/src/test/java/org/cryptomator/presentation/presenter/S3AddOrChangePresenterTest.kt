package org.cryptomator.presentation.presenter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.ConnectToS3UseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.S3AddOrChangeView
import org.cryptomator.presentation.workflow.PermissionsResult
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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

	private lateinit var inTest: S3AddOrChangePresenter

	@BeforeEach
	fun setup() {
		whenever(view.activity()).thenReturn(activity)
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

	private fun connectResultHandler(): ResultHandler<Void> {
		inTest.authenticate("encryptedAccessKey", "encryptedSecretKey", "vaults", "http://192.168.178.20:9000", "eu-central-1", null, "MinIO")
		val handler = argumentCaptor<ResultHandler<Void>>()
		verify(connectToS3Launcher).run(handler.capture())
		return handler.firstValue
	}
}
