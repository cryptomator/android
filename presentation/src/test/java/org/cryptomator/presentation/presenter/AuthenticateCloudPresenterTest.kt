package org.cryptomator.presentation.presenter

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetUsernameUseCase
import org.cryptomator.domain.usecases.cloud.ReauthenticateBoxUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.intent.AuthenticateCloudIntent
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.SmbCloudModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.S3AddOrChangeActivity
import org.cryptomator.presentation.ui.activity.SmbAddOrChangeActivity
import org.cryptomator.presentation.ui.activity.view.AuthenticateCloudView
import org.cryptomator.presentation.workflow.AddExistingVaultWorkflow
import org.cryptomator.presentation.workflow.CreateNewVaultWorkflow
import org.cryptomator.presentation.workflow.PermissionsResult
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.Serializable

class AuthenticateCloudPresenterTest {

	private val cloud = SmbCloud.aSmbCloud().withId(1L).withUrl("smb://nas/share").withUsername("alice").withPassword("encryptedSecret").build()
	private val addOrChangeCloudConnectionUseCase = mock<AddOrChangeCloudConnectionUseCase>()
	private val addOrChangeCloudConnectionLauncher = mock<AddOrChangeCloudConnectionUseCase.Launcher>()
	private val cloudModelMapper = mock<CloudModelMapper>()
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
		whenever(intent.cloud()).thenReturn(SmbCloudModel(cloud))
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
	fun testGrantedPermissionSavesCloudSoTheCallerRetries() {
		inTest.authenticateCloudLocalNetworkPermissionResult(PermissionsResult(mock(), true), SmbCloudModel(cloud))

		verify(addOrChangeCloudConnectionUseCase).withCloud(cloud)
		verify(addOrChangeCloudConnectionLauncher).run(any())
		verify(view, never()).finish()
	}

	@Test
	fun testDeniedPermissionExplainsAndFinishes() {
		inTest.authenticateCloudLocalNetworkPermissionResult(PermissionsResult(mock(), false), SmbCloudModel(cloud))

		verify(view).showMessage(eq(R.string.permission_snackbar_local_network), anyVararg())
		verify(view).finish()
		verify(addOrChangeCloudConnectionUseCase, never()).withCloud(any())
	}

	@Test
	fun testWrongSmbCredentialsOpenTheSmbSettingsOnce() {
		whenever(intent.error()).thenReturn(WrongCredentialsException(cloud))

		withSettingsScreens { targets, intents ->
			inTest.resumed()
			// once more after returning from the settings
			inTest.resumed()

			assertThat(targets, `is`(listOf<Any?>(SmbAddOrChangeActivity::class.java)))
			verify(activity).startActivityForResult(eq(intents.constructed()[0]), any())
			verify(view, never()).finish()
		}
	}

	@Test
	fun testWrongS3CredentialsOpenTheS3Settings() {
		val s3Cloud = S3Cloud.aS3Cloud().withId(2L).withAccessKey("encryptedAccessKey").withSecretKey("encryptedSecretKey").withS3Bucket("vaults").withDisplayName("MinIO").build()
		whenever(intent.cloud()).thenReturn(S3CloudModel(s3Cloud))
		whenever(intent.error()).thenReturn(WrongCredentialsException(s3Cloud))

		withSettingsScreens { targets, intents ->
			inTest.resumed()

			assertThat(targets, `is`(listOf<Any?>(S3AddOrChangeActivity::class.java)))
			verify(activity).startActivityForResult(eq(intents.constructed()[0]), any())
		}
	}

	@Test
	fun testOtherSmbErrorsFailTheAuthentication() {
		whenever(intent.error()).thenReturn(NoAuthenticationProvidedException(cloud))

		inTest.resumed()

		verify(activity, never()).startActivityForResult(any(), any())
		verify(view).finish()
	}

	@Test
	fun testSavedSettingsCompleteTheAuthenticationAfterTheScreenWasRecreated() {
		val changedCloud = SmbCloud.aCopyOf(cloud).withPassword("encryptedSecret2").build()
		val settingsResult = mock<Intent>()
		whenever(settingsResult.getSerializableExtra(CloudConnectionListPresenter.SELECTED_CLOUD)).thenReturn(changedCloud)
		val recreated = recreatedWhileTheSettingsAreOpen()

		val changedCloudModel = SmbCloudModel(changedCloud)
		whenever(cloudModelMapper.toModel(changedCloud)).thenReturn(changedCloudModel)

		withSettingsScreens { targets, _ ->
			recreated.presenter.onActivityResult(recreated.requestCode, Activity.RESULT_OK, settingsResult)
			recreated.presenter.resumed()

			assertThat(targets, `is`(emptyList<Any?>()))
		}
		verify(addOrChangeCloudConnectionUseCase).withCloud(changedCloud)
		val saved = argumentCaptor<ResultHandler<Void?>>()
		verify(addOrChangeCloudConnectionLauncher).run(saved.capture())
		// the screen only closes once the cloud is saved
		verify(view, never()).finish()

		Mockito.mockConstruction(Intent::class.java).use { intents ->
			saved.firstValue.onSuccess(null)

			val result = intents.constructed().single()
			verify(result).putExtra(Presenter.SINGLE_RESULT, changedCloudModel as Serializable)
			verify(activity).setResult(Activity.RESULT_OK, result)
			verify(view).finish()
		}
	}

	@Test
	fun testCancelledSettingsFailTheAuthenticationAfterTheScreenWasRecreated() {
		val recreated = recreatedWhileTheSettingsAreOpen()

		withSettingsScreens { targets, _ ->
			recreated.presenter.onActivityResult(recreated.requestCode, Activity.RESULT_CANCELED, null)

			assertThat(targets, `is`(emptyList<Any?>()))
		}
		verify(view).finish()
		verify(addOrChangeCloudConnectionUseCase, never()).withCloud(any())
	}

	private class Recreated(val presenter: AuthenticateCloudPresenter, val requestCode: Int)

	/**
	 * Opens the settings because of wrong credentials, then saves and restores the state into a new presenter, as for a
	 * recreated screen.
	 */
	private fun recreatedWhileTheSettingsAreOpen(): Recreated {
		whenever(intent.error()).thenReturn(WrongCredentialsException(cloud))
		val state = bundle()
		val requestCode = argumentCaptor<Int>()
		withSettingsScreens { targets, _ ->
			inTest.resumed()
			assertThat(targets, `is`(listOf<Any?>(SmbAddOrChangeActivity::class.java)))
			verify(activity).startActivityForResult(any(), requestCode.capture())
		}
		InstanceStates.save(inTest, state)
		val recreated = presenter()
		InstanceStates.restore(recreated, state)
		return Recreated(recreated, requestCode.firstValue)
	}

	private fun presenter(): AuthenticateCloudPresenter {
		return AuthenticateCloudPresenter(
			mock<ExceptionHandlers>(),
			cloudModelMapper,
			addOrChangeCloudConnectionUseCase,
			mock<GetCloudsUseCase>(),
			mock<GetUsernameUseCase>(),
			mock<ReauthenticateBoxUseCase>(),
			mock<AddExistingVaultWorkflow>(),
			mock<CreateNewVaultWorkflow>()
		).also { it.view = view }
	}

	private fun bundle(): Bundle {
		val values = HashMap<String, Any?>()
		val bundle = mock<Bundle>()
		doAnswer { values[it.getArgument(0)] = it.getArgument<Any?>(1) }.whenever(bundle).putBoolean(any(), any())
		doAnswer { values[it.getArgument(0)] = it.getArgument<Any?>(1) }.whenever(bundle).putInt(any(), any())
		doAnswer { values[it.getArgument(0)] = it.getArgument<Any?>(1) }.whenever(bundle).putSerializable(any(), anyOrNull())
		whenever(bundle.getBoolean(any())).thenAnswer { values[it.getArgument(0)] as Boolean? ?: false }
		whenever(bundle.getInt(any())).thenAnswer { values[it.getArgument(0)] as Int? ?: 0 }
		whenever(bundle.getSerializable(any())).thenAnswer { values[it.getArgument(0)] }
		return bundle
	}

	private fun withSettingsScreens(block: (targets: List<Any?>, intents: MockedConstruction<Intent>) -> Unit) {
		Mockito.mockStatic(Toast::class.java).use { toasts ->
			val toast = mock<Toast>()
			toasts.`when`<Toast> { Toast.makeText(anyOrNull(), anyOrNull<CharSequence>(), any()) }.thenReturn(toast)
			val targets = mutableListOf<Any?>()
			Mockito.mockConstruction(Intent::class.java) { _, construction -> targets.add(construction.arguments().getOrNull(1)) }.use { intents ->
				block(targets, intents)
			}
		}
	}
}
