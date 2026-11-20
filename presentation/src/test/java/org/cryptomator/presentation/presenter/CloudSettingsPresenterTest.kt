package org.cryptomator.presentation.presenter

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.DropboxCloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.GetAllCloudsUseCase
import org.cryptomator.domain.usecases.cloud.GetCloudsUseCase
import org.cryptomator.domain.usecases.cloud.LogoutCloudUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.mappers.CloudModelMapper
import org.cryptomator.presentation.ui.activity.view.CloudSettingsView
import org.cryptomator.util.FlavorConfig
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class CloudSettingsPresenterTest {

	private val getAllCloudsUseCase = mock<GetAllCloudsUseCase>()
	private val view = mock<CloudSettingsView>()

	private lateinit var inTest: CloudSettingsPresenter

	@BeforeEach
	fun setup() {
		inTest = CloudSettingsPresenter(getAllCloudsUseCase, mock<GetCloudsUseCase>(), mock<LogoutCloudUseCase>(), CloudModelMapper(), mock<ExceptionHandlers>())
		inTest.view = view
	}

	@Test
	fun testProviderWithConnectionsGetsOneRowNextToWebDav() {
		val rendered = renderedCloudTypesFor(webDavCloud(1L, null), webDavCloud(2L, WebDavProvider.NEXTCLOUD), webDavCloud(3L, WebDavProvider.KOOFR), webDavCloud(4L, WebDavProvider.KOOFR))

		assertThat(rendered.filter { it == CloudTypeModel.WEBDAV || it.webDavProvider != null }, `is`(listOf(CloudTypeModel.WEBDAV, CloudTypeModel.KOOFR, CloudTypeModel.NEXTCLOUD)))
	}

	@Test
	fun testProviderWithoutConnectionsGetsNoRow() {
		val rendered = renderedCloudTypesFor(webDavCloud(1L, null))

		assertThat(rendered.filter { it.webDavProvider != null }, `is`(emptyList()))
		assertThat(rendered.count { it == CloudTypeModel.WEBDAV }, `is`(1))
	}

	@Test
	fun testSingleLoginCloudKeepsItsRowWhereTheFlavorSupportsIt() {
		val rendered = renderedCloudTypesFor(DropboxCloud.aDropboxCloud().withId(1L).build())

		assertThat(rendered.count { it == CloudTypeModel.DROPBOX }, `is`(if (FlavorConfig.isLiteFlavor) 0 else 1))
	}

	@Test
	fun testStoredMultiInstanceCloudsShareOneRow() {
		val rendered = renderedCloudTypesFor(S3Cloud.aS3Cloud().withId(1L).build(), S3Cloud.aS3Cloud().withId(2L).build())

		assertThat(rendered.count { it == CloudTypeModel.S3 }, `is`(1))
	}

	private fun renderedCloudTypesFor(vararg clouds: Cloud): List<CloudTypeModel> {
		inTest.loadClouds()

		val cloudsHandler = argumentCaptor<ResultHandler<List<Cloud>>>()
		verify(getAllCloudsUseCase).run(cloudsHandler.capture())
		cloudsHandler.firstValue.onSuccess(clouds.toList())

		val rendered = argumentCaptor<List<CloudModel>>()
		verify(view).render(rendered.capture())
		return rendered.firstValue.map { it.cloudType() }
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
}
