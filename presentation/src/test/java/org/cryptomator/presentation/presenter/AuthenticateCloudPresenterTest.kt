package org.cryptomator.presentation.presenter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.exception.authentication.WebDavServerNotFoundException
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetUsernameUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.AuthenticateCloudIntent
import org.cryptomator.presentation.model.WebDavCloudModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.AuthenticateCloudView
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.PermissionsResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AuthenticateCloudPresenterTest {

	private val cloud = WebDavCloud.aWebDavCloudCloud().withId(1L).withUrl("http://192.168.178.20/webdav").withUsername("alice").withPassword("encryptedSecret").build()
	private val addOrChangeCloudConnectionUseCase = mock<AddOrChangeCloudConnectionUseCase>()
	private val addOrChangeCloudConnectionLauncher = mock<AddOrChangeCloudConnectionUseCase.Launcher>()
	private val activity = mock<Activity>()
	private val view = mock<AuthenticateCloudView>()
	private val intent = mock<AuthenticateCloudIntent>()

	private lateinit var inTest: AuthenticateCloudPresenter

	@BeforeEach
	fun setup() {
		val context = mock<Context>()
		whenever(context.getString(any())).thenReturn("%s")
		whenever(view.activity()).thenReturn(activity)
		whenever(view.context()).thenReturn(context)
		whenever(view.intent()).thenReturn(intent)
		whenever(intent.cloud()).thenReturn(WebDavCloudModel(cloud))
		whenever(intent.error()).thenReturn(LocalNetworkPermissionMissingException(cloud))
		whenever(addOrChangeCloudConnectionUseCase.withCloud(any())).thenReturn(addOrChangeCloudConnectionLauncher)
		inTest = presenter()
	}

	@Test
	fun testLocalNetworkPermissionIsRequestedOnlyOnce() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)

				inTest.resumed()
				// once more after the permission dialog closed
				inTest.resumed()

				activityCompat.verify({ ActivityCompat.requestPermissions(any(), eq(arrayOf<String?>(Manifest.permission.ACCESS_LOCAL_NETWORK)), any()) }, times(1))
				// the cloud's own strategy would fail the authentication and close the screen
				verify(view, never()).finish()
			}
		}
	}

	@Test
	fun testLocalNetworkPermissionIsNotRequestedAgainAfterTheScreenWasRecreated() {
		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)
				inTest.resumed()
				activityCompat.verify({ ActivityCompat.requestPermissions(any(), eq(arrayOf<String?>(Manifest.permission.ACCESS_LOCAL_NETWORK)), any()) }, times(1))

				val state = bundle()
				InstanceStates.save(inTest, state)
				val recreated = presenter()
				InstanceStates.restore(recreated, state)
				recreated.resumed()

				activityCompat.verify({ ActivityCompat.requestPermissions(any(), eq(arrayOf<String?>(Manifest.permission.ACCESS_LOCAL_NETWORK)), any()) }, times(1))
				verify(view, never()).finish()
			}
		}
	}

	@Test
	fun testOtherErrorsAreHandledByTheCloudsStrategy() {
		whenever(intent.error()).thenReturn(WebDavServerNotFoundException(cloud))

		Mockito.mockStatic(ContextCompat::class.java).use { contextCompat ->
			Mockito.mockStatic(ActivityCompat::class.java).use { activityCompat ->
				contextCompat.`when`<Int> { ContextCompat.checkSelfPermission(any(), any()) }.thenReturn(PackageManager.PERMISSION_DENIED)

				inTest.resumed()

				verify(view).showMessage(eq(R.string.error_server_not_found), anyVararg())
				verify(view).finish()
				activityCompat.verify({ ActivityCompat.requestPermissions(any(), any(), any()) }, never())
			}
		}
	}

	@Test
	fun testGrantedPermissionSavesCloudSoTheCallerRetries() {
		inTest.authenticateCloudLocalNetworkPermissionResult(PermissionsResult(mock(), true), WebDavCloudModel(cloud))

		verify(addOrChangeCloudConnectionUseCase).withCloud(cloud)
		verify(addOrChangeCloudConnectionLauncher).run(any())
		verify(view, never()).finish()
	}

	@Test
	fun testDeniedPermissionExplainsAndFinishes() {
		inTest.authenticateCloudLocalNetworkPermissionResult(PermissionsResult(mock(), false), WebDavCloudModel(cloud))

		verify(view).showMessage(eq(R.string.permission_snackbar_local_network), anyVararg())
		verify(view).finish()
		verify(addOrChangeCloudConnectionUseCase, never()).withCloud(any())
	}

	private fun presenter(): AuthenticateCloudPresenter {
		return AuthenticateCloudPresenter(
			mock<ExceptionHandlers>(),
			mock<CloudModelMapper>(),
			addOrChangeCloudConnectionUseCase,
			mock<GetCloudsUseCase>(),
			mock<GetUsernameUseCase>(),
			mock<AddExistingVaultWorkflow>(),
			mock<CreateNewVaultWorkflow>()
		).also { it.view = view }
	}

	private fun bundle(): Bundle {
		val values = HashMap<String, Any?>()
		val bundle = mock<Bundle>()
		doAnswer { values[it.getArgument(0)] = it.getArgument<Any?>(1) }.whenever(bundle).putBoolean(any(), any())
		whenever(bundle.getBoolean(any())).thenAnswer { values[it.getArgument(0)] as Boolean? ?: false }
		return bundle
	}
}
