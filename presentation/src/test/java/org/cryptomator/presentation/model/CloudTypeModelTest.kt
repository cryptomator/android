package org.cryptomator.presentation.model

import org.cryptomator.domain.CloudType
import org.cryptomator.domain.DropboxCloud
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.Vault
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class CloudTypeModelTest {

	@ParameterizedTest
	@EnumSource(WebDavProvider::class)
	fun testEveryProviderHasItsOwnMultiInstanceWebDavType(provider: WebDavProvider) {
		val cloudType = CloudTypeModel.valueOf(provider)

		assertEquals(provider, cloudType.webDavProvider)
		assertEquals(CloudType.WEBDAV, CloudTypeModel.valueOf(cloudType))
		assertTrue(cloudType.isMultiInstance)
		assertNotEquals(0, cloudType.cloudImageResource)
		assertNotEquals(0, cloudType.vaultImageResource)
		assertNotEquals(0, cloudType.vaultSelectedImageResource)
	}

	@ParameterizedTest
	@EnumSource(CloudType::class)
	fun testEveryDomainTypeMapsToATypeWithoutProvider(type: CloudType) {
		val cloudType = CloudTypeModel.valueOf(type)

		assertNull(cloudType.webDavProvider)
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
	fun testCloudOfAnotherTypeResolvesToItsOwnType() {
		val clouds = listOf(DropboxCloud.aDropboxCloud().withId(1L).build(), S3Cloud.aS3Cloud().withId(2L).build(), LocalStorageCloud.aLocalStorage().withId(3L).build())

		assertEquals(listOf(CloudTypeModel.DROPBOX, CloudTypeModel.S3, CloudTypeModel.LOCAL), clouds.map { CloudTypeModel.valueOf(it) })
		assertEquals(listOf(CloudTypeModel.DROPBOX, CloudTypeModel.S3, CloudTypeModel.LOCAL), clouds.map { VaultModel(vault().withCloud(it).build()).cloudType })
	}

	@Test
	fun testVaultWithoutCloudResolvesToItsStoredType() {
		assertEquals(CloudTypeModel.WEBDAV, VaultModel(vault().withCloudType(CloudType.WEBDAV).build()).cloudType)
		assertEquals(CloudTypeModel.LOCAL, VaultModel(vault().withCloudType(CloudType.LOCAL).build()).cloudType)
	}

	private fun vault(): Vault.Builder {
		return Vault.aVault().withId(1L).withName("vault").withPath("/vault").withPosition(0)
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
