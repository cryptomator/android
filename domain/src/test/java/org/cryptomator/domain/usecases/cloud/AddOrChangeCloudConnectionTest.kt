package org.cryptomator.domain.usecases.cloud

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider
import org.cryptomator.domain.exception.CloudAlreadyExistsException
import org.cryptomator.domain.repository.CloudRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AddOrChangeCloudConnectionTest {

	private val cloudRepository: CloudRepository = mock()

	private val storedGenericCloud = webDavCloud(id = 42L, provider = null, password = "oldPassword")

	@Test
	fun `a new connection is stored`() {
		storedClouds()
		val cloud = webDavCloud(id = null, provider = WebDavProvider.KOOFR)

		AddOrChangeCloudConnection(cloudRepository, cloud).execute()

		verify(cloudRepository).store(cloud)
	}

	@Test
	fun `a new provider connection adopts the matching connection without provider`() {
		storedClouds(storedGenericCloud)

		AddOrChangeCloudConnection(cloudRepository, webDavCloud(id = null, provider = WebDavProvider.KOOFR, password = "newPassword")).execute()

		val stored = storedCloud()
		assertEquals(42L, stored.id())
		assertEquals(WebDavProvider.KOOFR, stored.provider())
		assertEquals("newPassword", stored.password())
	}

	@Test
	fun `a new connection without provider matching an existing one is rejected`() {
		storedClouds(storedGenericCloud)

		assertThrows<CloudAlreadyExistsException> {
			AddOrChangeCloudConnection(cloudRepository, webDavCloud(id = null, provider = null)).execute()
		}
		verify(cloudRepository, never()).store(any())
	}

	@Test
	fun `a new provider connection matching one of another provider is rejected`() {
		storedClouds(webDavCloud(id = 42L, provider = WebDavProvider.KDRIVE))

		assertThrows<CloudAlreadyExistsException> {
			AddOrChangeCloudConnection(cloudRepository, webDavCloud(id = null, provider = WebDavProvider.KOOFR)).execute()
		}
		verify(cloudRepository, never()).store(any())
	}

	@Test
	fun `a new connection of another type matching an existing one is rejected`() {
		whenever(cloudRepository.clouds(CloudType.S3)).thenReturn(listOf(s3Cloud(42L)))

		assertThrows<CloudAlreadyExistsException> {
			AddOrChangeCloudConnection(cloudRepository, s3Cloud(null)).execute()
		}
		verify(cloudRepository, never()).store(any())
	}

	@Test
	fun `a changed provider connection matching another connection is rejected`() {
		storedClouds(storedGenericCloud, webDavCloud(id = 43L, provider = WebDavProvider.KOOFR, url = "https://other.example.org"))

		assertThrows<CloudAlreadyExistsException> {
			AddOrChangeCloudConnection(cloudRepository, webDavCloud(id = 43L, provider = WebDavProvider.KOOFR)).execute()
		}
		verify(cloudRepository, never()).store(any())
	}

	@Test
	fun `a changed connection is stored under its own id`() {
		storedClouds(storedGenericCloud)
		val changed = webDavCloud(id = 42L, provider = null, password = "newPassword")

		AddOrChangeCloudConnection(cloudRepository, changed).execute()

		val stored = storedCloud()
		assertEquals(42L, stored.id())
		assertNull(stored.provider())
		assertEquals("newPassword", stored.password())
	}

	private fun storedClouds(vararg clouds: Cloud) {
		whenever(cloudRepository.clouds(CloudType.WEBDAV)).thenReturn(clouds.toList())
	}

	private fun storedCloud(): WebDavCloud {
		val stored = argumentCaptor<Cloud>()
		verify(cloudRepository).store(stored.capture())
		return stored.firstValue as WebDavCloud
	}

	private fun s3Cloud(id: Long?): S3Cloud {
		return S3Cloud.aS3Cloud().withId(id).withS3Endpoint("https://s3.example.org").withS3Region("region").withS3Bucket("bucket").build()
	}

	private fun webDavCloud(id: Long?, provider: WebDavProvider?, password: String = "password", url: String = "https://app.koofr.net/dav/Koofr"): WebDavCloud {
		return WebDavCloud.aWebDavCloudCloud() //
			.withId(id) //
			.withUrl(url) //
			.withUsername("alice") //
			.withPassword(password) //
			.withProvider(provider) //
			.build()
	}
}
