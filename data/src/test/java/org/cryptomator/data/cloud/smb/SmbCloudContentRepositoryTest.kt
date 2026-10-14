package org.cryptomator.data.cloud.smb

import android.content.Context
import com.hierynomus.smbj.share.DiskShare
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.util.Optional
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SmbCloudContentRepositoryTest {

	private val cloud = SmbCloud.aSmbCloud().withId(1L).withUrl("smb://nas/share").withUsername("alice").withPassword("encryptedPassword").build()

	// an edited connection keeps its ID, by which the repository is cached
	private val edited = SmbCloud.aCopyOf(cloud).withUrl("smb://other-nas/share").withUsername("bob").withPassword("otherEncryptedPassword").build()
	private val connectionPool = mock<SmbConnectionPool>()
	private val share = mock<DiskShare>()
	private val inTest = SmbCloudContentRepository(cloud, connectionPool, mock<Context>())

	@BeforeEach
	fun setup() {
		whenever(share.folderExists(any())).thenReturn(true)
		whenever(connectionPool.withShare<Any?>(any(), any(), any())).thenAnswer {
			it.getArgument<(DiskShare) -> Any?>(2).invoke(share)
		}
	}

	@Test
	fun testEditedConnectionIsCheckedWithItsNewSettings() {
		assertThat(inTest.checkAuthenticationAndRetrieveCurrentAccount(edited), `is`("bob"))

		val config = argumentCaptor<SmbConnectionPool.Config>()
		verify(connectionPool).withShare<Any?>(config.capture(), any(), any())
		assertThat(config.firstValue.location.host, `is`("other-nas"))
		assertThat(config.firstValue.username, `is`("bob"))
		assertThat(config.firstValue.encryptedPassword, `is`("otherEncryptedPassword"))
	}

	@ParameterizedTest
	@ValueSource(strings = ["exists", "list", "create", "moveFile", "moveFolder", "write", "read", "delete"])
	fun testEachOperationUsesTheSettingsOfItsNode(operation: String) {
		val oldRoot = inTest.root(cloud)
		val root = inTest.root(edited)
		val file = inTest.file(root, "a.c9r")
		val folder = inTest.folder(root, "d")
		val fileTarget = inTest.file(root, "b.c9r")
		val folderTarget = inTest.folder(root, "e")
		val operationOnEditedNodes: () -> Any = when (operation) {
			"exists" -> { -> inTest.exists(file) }
			"list" -> { -> inTest.list(root) }
			"create" -> { -> inTest.create(folder) }
			"moveFile" -> { -> inTest.move(file, fileTarget) }
			"moveFolder" -> { -> inTest.move(folder, folderTarget) }
			"write" -> { -> inTest.write(file, dataSource(), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, true, 1L) }
			"read" -> { -> inTest.read(file, null, ByteArrayOutputStream(), ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD) }
			else -> { -> inTest.delete(file) }
		}
		// meanwhile, a node with the old settings was used, e.g. by a vault unlocked before the edit
		inTest.exists(oldRoot)

		// failures of the stubbed share don't matter, only which connection is asked for
		runCatching { operationOnEditedNodes() }

		val config = argumentCaptor<SmbConnectionPool.Config>()
		verify(connectionPool, atLeast(2)).withShare<Any?>(config.capture(), any(), any())
		assertThat(config.firstValue.encryptedPassword, `is`("encryptedPassword"))
		assertThat(config.allValues.drop(1).map { it.encryptedPassword }.toSet(), `is`(setOf("otherEncryptedPassword")))
	}

	@Test
	fun testReplacedSettingsAreLoggedOff() {
		inTest.exists(inTest.root(cloud))

		inTest.exists(inTest.root(edited))

		val config = argumentCaptor<SmbConnectionPool.Config>()
		verify(connectionPool).logout(config.capture())
		assertThat(config.firstValue.encryptedPassword, `is`("encryptedPassword"))
	}

	@Test
	fun testLogoutUsesTheGivenSettings() {
		inTest.logout(edited)

		val config = argumentCaptor<SmbConnectionPool.Config>()
		verify(connectionPool, times(2)).logout(config.capture())
		// first the replaced settings, then the given ones
		assertThat(config.firstValue.encryptedPassword, `is`("encryptedPassword"))
		assertThat(config.secondValue.encryptedPassword, `is`("otherEncryptedPassword"))
	}

	private fun dataSource(): DataSource {
		val data = mock<DataSource>()
		whenever(data.open(any())).thenReturn(ByteArrayInputStream(byteArrayOf(1)))
		whenever(data.modifiedDate(any())).thenReturn(Optional.empty())
		return data
	}
}
