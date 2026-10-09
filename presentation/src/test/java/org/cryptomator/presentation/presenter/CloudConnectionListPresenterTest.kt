package org.cryptomator.presentation.presenter

import android.os.Bundle
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.AddOrChangeCloudConnectionUseCase
import org.cryptomator.domain.usecases.cloud.AuthenticateBoxUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetSharepointDrivesUseCase
import org.cryptomator.domain.usecases.cloud.GetUsernameUseCase
import org.cryptomator.domain.usecases.cloud.RemoveCloudUseCase
import org.cryptomator.domain.usecases.vault.DeleteVaultsUseCase
import org.cryptomator.domain.usecases.vault.GetVaultListUseCase
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.CloudConnectionListView
import org.cryptomator.presentation.ui.dialog.ChooseSharepointDriveDialog
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedConstruction
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.Serializable

class CloudConnectionListPresenterTest {

	private val getCloudsUseCase = mock<GetCloudsUseCase>()
	private val getCloudsLauncher = mock<GetCloudsUseCase.Launcher>()
	private val getUsernameUseCase = mock<GetUsernameUseCase>()
	private val getUsernameLauncher = mock<GetUsernameUseCase.Launcher>()
	private val addOrChangeCloudConnectionUseCase = mock<AddOrChangeCloudConnectionUseCase>()
	private val addOrChangeCloudConnectionLauncher = mock<AddOrChangeCloudConnectionUseCase.Launcher>()
	private val getSharepointDrivesUseCase = mock<GetSharepointDrivesUseCase>()
	private val getSharepointDrivesLauncher = mock<GetSharepointDrivesUseCase.Launcher>()
	private val exceptionHandlers = mock<ExceptionHandlers>()
	private val view = mock<CloudConnectionListView>()

	private val storedCloud = SharepointCloud.aSharepointCloud() //
		.withId(42L) //
		.withAccessToken("oldAccessToken") //
		.withUsername("user@contoso.com") //
		.withSiteUrl("https://contoso.sharepoint.com/sites/team") //
		.withDriveId("driveId") //
		.withDriveName("Documents") //
		.build()

	private lateinit var inTest: CloudConnectionListPresenter

	@BeforeEach
	fun setup() {
		whenever(getCloudsUseCase.withCloudType(any())).thenReturn(getCloudsLauncher)
		whenever(getUsernameUseCase.withCloud(any())).thenReturn(getUsernameLauncher)
		whenever(addOrChangeCloudConnectionUseCase.withCloud(any())).thenReturn(addOrChangeCloudConnectionLauncher)
		whenever(getSharepointDrivesUseCase.withCloud(any())).thenReturn(getSharepointDrivesLauncher)
		inTest = CloudConnectionListPresenter(
			getCloudsUseCase,
			getUsernameUseCase,
			getSharepointDrivesUseCase,
			mock<AuthenticateBoxUseCase>(),
			mock<RemoveCloudUseCase>(),
			addOrChangeCloudConnectionUseCase,
			mock<GetVaultListUseCase>(),
			mock<DeleteVaultsUseCase>(),
			mock<CloudModelMapper>(),
			exceptionHandlers
		)
		inTest.view = view
		inTest.setSelectedCloudType(CloudTypeModel.SHAREPOINT)
	}

	@Test
	fun testChosenDriveOfStoredConnectionRefreshesItsAccessToken() {
		val chosenDrive = SharepointCloud.aCopyOf(storedCloud).withId(null).withAccessToken("newAccessToken").build()

		val savedCloud = savedCloudAfterChoosing(chosenDrive, storedClouds = listOf(storedCloud))

		assertThat(savedCloud.id(), `is`(42L))
		assertThat(savedCloud.accessToken(), `is`("newAccessToken"))
		assertThat(savedCloud.driveId(), `is`("driveId"))
	}

	@Test
	fun testChosenOtherDriveOfSameUserIsSavedAsNewConnection() {
		val chosenDrive = SharepointCloud.aCopyOf(storedCloud).withId(null).withAccessToken("newAccessToken").withDriveId("otherDriveId").build()

		val savedCloud = savedCloudAfterChoosing(chosenDrive, storedClouds = listOf(storedCloud))

		assertThat(savedCloud.id(), `is`(nullValue()))
		assertThat(savedCloud.accessToken(), `is`("newAccessToken"))
		assertThat(savedCloud.driveId(), `is`("otherDriveId"))
	}

	@Test
	fun testSiteWithoutDrivesShowsMessageAfterCompletingProgress() {
		drivesHandlerAfterLoading().onSuccess(emptyList())

		val inOrder = inOrder(view)
		inOrder.verify(view).showProgress(ProgressModel.COMPLETED)
		inOrder.verify(view).showMessage(R.string.screen_cloud_connections_msg_no_sharepoint_drives)
	}

	@Test
	fun testDrivesLoadedWhilePausedAreShownOnceOnResume() {
		inTest.pause()
		drivesHandlerAfterLoading().onSuccess(emptyList())

		verify(view).showProgress(ProgressModel.COMPLETED)
		verify(view, never()).showMessage(any<Int>(), anyVararg())
		inTest.resume()
		inTest.pause()
		inTest.resume()
		verify(view, times(1)).showMessage(R.string.screen_cloud_connections_msg_no_sharepoint_drives)
	}

	@Test
	fun testSiteWithDrivesShowsPickerAfterCompletingProgress() {
		Mockito.mockConstruction(Bundle::class.java).use { bundles ->
			val drives = discoveredDrives()
			drivesHandlerAfterLoading().onSuccess(drives)

			val inOrder = inOrder(view)
			inOrder.verify(view).showProgress(ProgressModel.COMPLETED)
			inOrder.verify(view).showDialog(any<ChooseSharepointDriveDialog>())
			assertThat(pickerDrivesOf(bundles), `is`<Serializable>(ArrayList(drives)))
		}
	}

	@Test
	fun testDrivesLoadedWhilePausedShowPickerOnceOnResume() {
		Mockito.mockConstruction(Bundle::class.java).use { bundles ->
			val drives = discoveredDrives()
			inTest.pause()
			drivesHandlerAfterLoading().onSuccess(drives)

			verify(view).showProgress(ProgressModel.COMPLETED)
			verify(view, never()).showDialog(any())
			inTest.resume()
			inTest.pause()
			inTest.resume()
			verify(view, times(1)).showDialog(any<ChooseSharepointDriveDialog>())
			assertThat(pickerDrivesOf(bundles), `is`<Serializable>(ArrayList(drives)))
		}
	}

	@Test
	fun testUnknownSiteShowsSiteNotFoundError() {
		drivesHandlerAfterLoading().onError(NoSuchCloudFileException("https://contoso.sharepoint.com/sites/team"))

		verify(view).showProgress(ProgressModel.COMPLETED)
		verify(view).showError(R.string.screen_cloud_connections_msg_sharepoint_site_not_found)
		verify(exceptionHandlers, never()).handle(any(), any())
	}

	@Test
	fun testOtherDiscoveryErrorIsHandledByExceptionHandlers() {
		val error = NetworkConnectionException()

		drivesHandlerAfterLoading().onError(error)

		verify(view).showProgress(ProgressModel.COMPLETED)
		verify(exceptionHandlers).handle(view, error)
	}

	private fun pickerDrivesOf(bundles: MockedConstruction<Bundle>): Serializable {
		val drives = argumentCaptor<Serializable>()
		verify(bundles.constructed().single()).putSerializable(eq("drives"), drives.capture())
		return drives.firstValue
	}

	private fun discoveredDrives(): List<SharepointCloud> {
		return listOf(
			SharepointCloud.aCopyOf(storedCloud).withId(null).withAccessToken("newAccessToken").build(),
			SharepointCloud.aCopyOf(storedCloud).withId(null).withAccessToken("newAccessToken").withDriveId("otherDriveId").withDriveName("Shared").build()
		)
	}

	private fun drivesHandlerAfterLoading(): ResultHandler<List<SharepointCloud>> {
		val skeleton = SharepointCloud.aCopyOf(storedCloud).withId(null).withDriveId(null).withDriveName(null).build()
		inTest.loadSharepointDrives(skeleton)

		verify(getSharepointDrivesUseCase).withCloud(skeleton)
		val drivesHandler = argumentCaptor<ResultHandler<List<SharepointCloud>>>()
		verify(getSharepointDrivesLauncher).run(drivesHandler.capture())
		return drivesHandler.firstValue
	}

	private fun savedCloudAfterChoosing(chosenDrive: SharepointCloud, storedClouds: List<Cloud>): SharepointCloud {
		inTest.onSharepointDriveChosen(chosenDrive)

		val usernameHandler = argumentCaptor<ResultHandler<String>>()
		verify(getUsernameLauncher).run(usernameHandler.capture())
		usernameHandler.firstValue.onSuccess(chosenDrive.username())

		val cloudsHandler = argumentCaptor<ResultHandler<List<Cloud>>>()
		verify(getCloudsUseCase).withCloudType(CloudType.SHAREPOINT)
		verify(getCloudsLauncher).run(cloudsHandler.capture())
		cloudsHandler.firstValue.onSuccess(storedClouds)

		val savedCloud = argumentCaptor<Cloud>()
		verify(addOrChangeCloudConnectionUseCase).withCloud(savedCloud.capture())
		return savedCloud.firstValue as SharepointCloud
	}
}
