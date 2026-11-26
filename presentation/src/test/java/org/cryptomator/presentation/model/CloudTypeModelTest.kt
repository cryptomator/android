package org.cryptomator.presentation.model

import android.content.Context
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.DropboxCloud
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.S3Provider
import org.cryptomator.domain.Vault
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider
import org.cryptomator.presentation.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CloudTypeModelTest {

	@ParameterizedTest
	@EnumSource(WebDavProvider::class)
	fun testEveryProviderHasItsOwnMultiInstanceWebDavType(provider: WebDavProvider) {
		val cloudType = CloudTypeModel.valueOf(provider)

		assertEquals(provider, cloudType.webDavProvider)
		assertTrue(cloudType.isProvider)
		assertEquals(CloudType.WEBDAV, CloudTypeModel.valueOf(cloudType))
		assertTrue(cloudType.isMultiInstance)
		assertNotEquals(0, cloudType.cloudImageResource)
		assertNotEquals(0, cloudType.vaultImageResource)
		assertNotEquals(0, cloudType.vaultSelectedImageResource)
	}

	@ParameterizedTest
	@EnumSource(S3Provider::class)
	fun testEveryProviderHasItsOwnMultiInstanceS3Type(provider: S3Provider) {
		val cloudType = CloudTypeModel.valueOf(provider)

		assertEquals(provider, cloudType.s3Provider)
		assertTrue(cloudType.isProvider)
		assertEquals(CloudType.S3, CloudTypeModel.valueOf(cloudType))
		assertTrue(cloudType.isMultiInstance)
		assertNotEquals(0, cloudType.cloudImageResource)
		assertNotEquals(0, cloudType.vaultImageResource)
		assertNotEquals(0, cloudType.vaultSelectedImageResource)
	}

	@ParameterizedTest
	@EnumSource(CloudType::class)
	fun testEveryDomainTypeMapsToATypeWithoutProvider(type: CloudType) {
		val cloudType = CloudTypeModel.valueOf(type)

		assertFalse(cloudType.isProvider)
		assertEquals(type, CloudTypeModel.valueOf(cloudType))
	}

	@Test
	fun testWebDavCloudWithProviderResolvesToTheProviderType() {
		val cloud = webDavCloud(WebDavProvider.KOOFR)

		assertEquals(CloudTypeModel.KOOFR, CloudTypeModel.valueOf(cloud))
		assertEquals(CloudTypeModel.KOOFR, WebDavCloudModel(cloud).cloudType())
		assertEquals(CloudTypeModel.KOOFR.displayNameResource, WebDavCloudModel(cloud).name())
		assertEquals(CloudTypeModel.KOOFR, VaultModel(vault().withCloud(cloud).build()).cloudType)
	}

	@Test
	fun testWebDavCloudWithoutProviderResolvesToWebDav() {
		val cloud = webDavCloud(null)

		assertEquals(CloudTypeModel.WEBDAV, CloudTypeModel.valueOf(cloud))
		assertEquals(CloudTypeModel.WEBDAV, WebDavCloudModel(cloud).cloudType())
		assertEquals(CloudTypeModel.WEBDAV, VaultModel(vault().withCloud(cloud).build()).cloudType)
	}

	@Test
	fun testS3CloudWithProviderResolvesToTheProviderType() {
		val cloud = s3Cloud(S3Provider.WASABI)

		assertEquals(CloudTypeModel.WASABI, CloudTypeModel.valueOf(cloud))
		assertEquals(CloudTypeModel.WASABI, S3CloudModel(cloud).cloudType())
		assertEquals(CloudTypeModel.WASABI.displayNameResource, S3CloudModel(cloud).name())
		assertEquals(CloudTypeModel.WASABI, VaultModel(vault().withCloud(cloud).build()).cloudType)
	}

	@Test
	fun testS3CloudWithoutProviderResolvesToS3() {
		val cloud = s3Cloud(null)

		assertEquals(CloudTypeModel.S3, CloudTypeModel.valueOf(cloud))
		assertEquals(CloudTypeModel.S3, S3CloudModel(cloud).cloudType())
		assertEquals(CloudTypeModel.S3, VaultModel(vault().withCloud(cloud).build()).cloudType)
	}

	@Test
	fun testCloudOfAnotherTypeResolvesToItsOwnType() {
		val clouds = listOf(DropboxCloud.aDropboxCloud().withId(1L).build(), S3Cloud.aS3Cloud().withId(2L).build(), LocalStorageCloud.aLocalStorage().withId(3L).build())

		assertEquals(listOf(CloudTypeModel.DROPBOX, CloudTypeModel.S3, CloudTypeModel.LOCAL), clouds.map { CloudTypeModel.valueOf(it) })
		assertEquals(listOf(CloudTypeModel.DROPBOX, CloudTypeModel.S3, CloudTypeModel.LOCAL), clouds.map { VaultModel(vault().withCloud(it).build()).cloudType })
	}

	@Test
	fun testTypesWithSeveralConnectionsHaveTheirConnectionsTitle() {
		val context = contextWithNumberedStrings()

		assertEquals("string ${R.string.screen_cloud_settings_onedrive_connections}", CloudTypeModel.ONEDRIVE.connectionsTitle(context))
		assertEquals("string ${R.string.screen_cloud_settings_sharepoint_connections}", CloudTypeModel.SHAREPOINT.connectionsTitle(context))
		assertEquals("string ${R.string.screen_cloud_settings_pcloud_connections}", CloudTypeModel.PCLOUD.connectionsTitle(context))
		assertEquals("string ${R.string.screen_cloud_settings_box_connections}", CloudTypeModel.BOX.connectionsTitle(context))
		assertEquals("string ${R.string.screen_cloud_settings_webdav_connections}", CloudTypeModel.WEBDAV.connectionsTitle(context))
		assertEquals("string ${R.string.screen_cloud_settings_s3_connections}", CloudTypeModel.S3.connectionsTitle(context))
		assertEquals("string ${R.string.screen_cloud_settings_local_storage_locations}", CloudTypeModel.LOCAL.connectionsTitle(context))
		assertEquals("string ${R.string.screen_cloud_settings_provider_connections} of string ${R.string.cloud_names_wasabi}", CloudTypeModel.WASABI.connectionsTitle(context))
		assertEquals("string ${R.string.screen_cloud_settings_provider_connections} of string ${R.string.cloud_names_koofr}", CloudTypeModel.KOOFR.connectionsTitle(context))
	}

	@ParameterizedTest
	@EnumSource(CloudTypeModel::class)
	fun testOnlyTypesWithSeveralConnectionsHaveAConnectionsTitle(cloudType: CloudTypeModel) {
		assertEquals(cloudType.isMultiInstance, cloudType.connectionsTitle(contextWithNumberedStrings()) != null)
	}

	@Test
	fun testVaultWithoutCloudResolvesToItsStoredType() {
		assertEquals(CloudTypeModel.WEBDAV, VaultModel(vault().withCloudType(CloudType.WEBDAV).build()).cloudType)
		assertEquals(CloudTypeModel.LOCAL, VaultModel(vault().withCloudType(CloudType.LOCAL).build()).cloudType)
	}

	private fun contextWithNumberedStrings(): Context {
		val context = mock<Context>()
		whenever(context.getString(any())).thenAnswer { "string ${it.arguments[0]}" }
		whenever(context.getString(any(), any())).thenAnswer { "string ${it.arguments[0]} of ${it.arguments[1]}" }
		return context
	}

	private fun vault(): Vault.Builder {
		return Vault.aVault().withId(1L).withName("vault").withPath("/vault").withPosition(0)
	}

	private fun s3Cloud(provider: S3Provider?): S3Cloud {
		return S3Cloud.aS3Cloud() //
			.withId(42L) //
			.withS3Endpoint("https://s3.example.org") //
			.withS3Region("region") //
			.withS3Bucket("bucket") //
			.withDisplayName("alice") //
			.withProvider(provider) //
			.build()
	}

	private fun webDavCloud(provider: WebDavProvider?): WebDavCloud {
		return WebDavCloud.aWebDavCloudCloud() //
			.withId(42L) //
			.withUrl("https://example.org/webdav") //
			.withUsername("alice") //
			.withPassword("password") //
			.withProvider(provider) //
			.build()
	}
}
