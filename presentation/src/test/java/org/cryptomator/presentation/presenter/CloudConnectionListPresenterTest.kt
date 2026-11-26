package org.cryptomator.presentation.presenter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.S3Provider
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider
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
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.S3CloudModel
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

	private val genericWebDavCloud = webDavCloud(1L, null)
	private val koofrCloud = webDavCloud(2L, WebDavProvider.KOOFR)
	private val nextcloudCloud = webDavCloud(3L, WebDavProvider.NEXTCLOUD)
	private val genericS3Cloud = s3Cloud(4L, null)
	private val wasabiCloud = s3Cloud(5L, S3Provider.WASABI)
	private val hetznerCloud = s3Cloud(6L, S3Provider.HETZNER)

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
			CloudModelMapper(),
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

	@Test
	fun testProviderListShowsOnlyConnectionsOfThatProvider() {
		inTest.setSelectedCloudType(CloudTypeModel.KOOFR)

		assertThat(shownCloudsAfterLoading(), `is`(listOf<Cloud>(koofrCloud)))
		verify(getCloudsUseCase).withCloudType(CloudType.WEBDAV)
	}

	@Test
	fun testWebDavListShowsOnlyConnectionsWithoutProvider() {
		inTest.setSelectedCloudType(CloudTypeModel.WEBDAV)

		assertThat(shownCloudsAfterLoading(), `is`(listOf<Cloud>(genericWebDavCloud)))
	}

	@Test
	fun testWebDavListIncludingProviderCloudsShowsEveryWebDavConnection() {
		inTest.setSelectedCloudType(CloudTypeModel.WEBDAV)
		inTest.setIncludeProviderClouds(true)

		assertThat(shownCloudsAfterLoading(), `is`(listOf<Cloud>(genericWebDavCloud, koofrCloud, nextcloudCloud)))
	}

	@Test
	fun testS3ProviderListShowsOnlyConnectionsOfThatProvider() {
		inTest.setSelectedCloudType(CloudTypeModel.WASABI)

		assertThat(shownCloudsAfterLoading(listOf(genericS3Cloud, wasabiCloud, hetznerCloud)), `is`(listOf<Cloud>(wasabiCloud)))
		verify(getCloudsUseCase).withCloudType(CloudType.S3)
	}

	@Test
	fun testS3ListShowsOnlyConnectionsWithoutProvider() {
		inTest.setSelectedCloudType(CloudTypeModel.S3)

		assertThat(shownCloudsAfterLoading(listOf(genericS3Cloud, wasabiCloud, hetznerCloud)), `is`(listOf<Cloud>(genericS3Cloud)))
	}

	@Test
	fun testAddingToAnS3ProviderListOpensTheSetupOfThatProvider() {
		val activity = activityOfView()
		inTest.setSelectedCloudType(CloudTypeModel.WASABI)

		Mockito.mockConstruction(Intent::class.java).use { intents ->
			inTest.onAddConnectionClicked()

			val s3Setup = intents.constructed().single()
			verify(s3Setup).putExtra("s3Provider", S3Provider.WASABI as Serializable)
			verify(activity).startActivityForResult(eq(s3Setup), any())
		}
	}

	@Test
	fun testChangingAnS3ProviderConnectionOpensItsSetup() {
		val activity = activityOfView()
		val wasabiCloudModel = S3CloudModel(wasabiCloud)

		Mockito.mockConstruction(Intent::class.java).use { intents ->
			inTest.onChangeCloudClicked(wasabiCloudModel)

			val s3Setup = intents.constructed().single()
			verify(s3Setup).putExtra("s3Cloud", wasabiCloudModel as Serializable)
			verify(activity).startActivityForResult(eq(s3Setup), any())
		}
	}

	@Test
	fun testListOfAnotherCloudTypeShowsItsConnections() {
		assertThat(shownCloudsAfterLoading(listOf(storedCloud)), `is`(listOf<Cloud>(storedCloud)))
	}

	private fun shownCloudsAfterLoading(storedClouds: List<Cloud> = listOf(genericWebDavCloud, koofrCloud, nextcloudCloud)): List<Cloud> {
		inTest.loadCloudList()

		val cloudsHandler = argumentCaptor<ResultHandler<List<Cloud>>>()
		verify(getCloudsLauncher).run(cloudsHandler.capture())
		cloudsHandler.firstValue.onSuccess(storedClouds)

		val shown = argumentCaptor<List<CloudModel>>()
		verify(view).showCloudModels(shown.capture())
		return shown.firstValue.map { it.toCloud() }
	}

	private fun activityOfView(): Activity {
		val activity = mock<Activity>()
		whenever(view.activity()).thenReturn(activity)
		whenever(view.context()).thenReturn(mock<Context>())
		return activity
	}

	private fun s3Cloud(id: Long, provider: S3Provider?): S3Cloud {
		return S3Cloud.aS3Cloud() //
			.withId(id) //
			.withS3Endpoint("https://s3.example.org") //
			.withS3Region("region") //
			.withS3Bucket("bucket-$id") //
			.withDisplayName("alice") //
			.withProvider(provider) //
			.build()
	}

	private fun webDavCloud(id: Long, provider: WebDavProvider?): WebDavCloud {
		return WebDavCloud.aWebDavCloudCloud() //
			.withId(id) //
			.withUrl("https://example.org/webdav/$id") //
			.withUsername("alice") //
			.withPassword("encryptedSecret") //
			.withProvider(provider) //
			.build()
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
