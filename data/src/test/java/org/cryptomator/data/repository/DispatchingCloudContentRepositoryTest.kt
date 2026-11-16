package org.cryptomator.data.repository

import org.cryptomator.data.cloud.CloudContentRepositoryFactories
import org.cryptomator.data.cloud.crypto.CryptoCloudContentRepositoryFactory
import org.cryptomator.data.util.NetworkConnectionCheck
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.repository.CloudContentRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DispatchingCloudContentRepositoryTest {

	private val factory = mock<CloudContentRepositoryFactory>()
	private val factories = mock<CloudContentRepositoryFactories>()
	private val repositoryOfUsedCloud = mock<CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>>()
	private val repositoryOfStoredCloud = mock<CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>>()

	private val usedCloud = webDavCloud("oldPassword")
	private val storedCloud = webDavCloud("newPassword")

	private lateinit var inTest: DispatchingCloudContentRepository

	@BeforeEach
	fun setup() {
		whenever(factories.iterator()).thenAnswer { listOf(factory).iterator() }
		whenever(factory.supports(any())).thenReturn(true)
		whenever(factory.cloudContentRepositoryFor(any())).thenReturn(repositoryOfUsedCloud)
		whenever(factory.cloudContentRepositoryFor(same(storedCloud))).thenReturn(repositoryOfStoredCloud)
		inTest = DispatchingCloudContentRepository(factories, mock<NetworkConnectionCheck>(), mock<CryptoCloudContentRepositoryFactory>())
	}

	@Test
	fun testStoredCloudReplacesTheRepositoryOfAUsedCloud() {
		inTest.root(usedCloud)

		inTest.updateCloudContentRepositoryFor(storedCloud)
		inTest.resolve(usedCloud, "/vault")

		verify(repositoryOfStoredCloud).resolve(usedCloud, "/vault")
		verify(repositoryOfUsedCloud, never()).resolve(any(), any())
	}

	@Test
	fun testStoredCloudWithoutARepositoryCreatesNone() {
		inTest.updateCloudContentRepositoryFor(storedCloud)

		verify(factory, never()).cloudContentRepositoryFor(any())
	}

	private fun webDavCloud(password: String): WebDavCloud {
		return WebDavCloud.aWebDavCloudCloud() //
			.withId(3L) //
			.withUrl("https://cloud.example.org/remote.php/webdav") //
			.withUsername("alice") //
			.withPassword(password) //
			.build()
	}
}
