package org.cryptomator.data.documents

import android.content.Context
import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.data.repository.DispatchingCloudContentRepository
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.repository.CloudRepository
import org.cryptomator.domain.repository.VaultRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.Executor

class DocumentsModuleTest {

	@TempDir
	lateinit var noBackupFilesDir: File

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).withUnlocked(true).build()
	private val metadataDao: DocumentMetadataDao = mock()
	private val cachedFileDao: CachedFileDao = mock()
	private val contentRepository: DispatchingCloudContentRepository = mock()
	private val inTest = DocumentsModule()

	@Test
	fun `the caches use the scheduler and events they are given`() {
		val listings = mutableListOf<Runnable>()
		var downloads = 0
		val direct = Executor { it.run() }
		val scheduler = CloudTaskScheduler({ listings.add(it) }, direct) {
			downloads++
			it.run()
		}
		val changedFolders = mutableListOf<VaultPath>()
		val events = VaultDocumentEvents().apply { addListener { changedFolders.add(it) } }
		val folder = VaultPath(vault, "a")
		whenever(metadataDao.load(1L, "a")).thenReturn(entity("a", android.provider.DocumentsContract.Document.MIME_TYPE_DIR))
		val context: Context = mock { on { noBackupFilesDir } doReturn noBackupFilesDir }

		val metadataCache = inTest.provideDocumentMetadataCache(metadataDao, scheduler, events, contentRepository, mock<CloudRepository>(), mock<VaultRepository>(), mock())
		val contentCache = inTest.provideDocumentContentCache(cachedFileDao, metadataCache, scheduler, contentRepository, context)

		metadataCache.refresh(folder)
		assertEquals(1, listings.size)
		listings.single().run()
		assertEquals(listOf(folder), changedFolders)
		assertTrue(File(noBackupFilesDir, DocumentContentCache.DIRECTORY_NAME).isDirectory)
		whenever(metadataDao.load(1L, "a")).thenReturn(entity("a", android.provider.DocumentsContract.Document.MIME_TYPE_DIR).apply { lastEnumeratedAt = System.currentTimeMillis() })
		whenever(metadataDao.loadVisible(1L, "a/x.txt")).thenReturn(entity("a/x.txt", "text/plain"))
		assertThrows<Exception> { contentCache.file(VaultPath(vault, "a/x.txt")) }
		assertEquals(1, downloads)
	}

	private fun entity(path: String, mimeType: String): DocumentMetadataEntity {
		return DocumentMetadataEntity().apply {
			id = 7L
			vaultId = 1L
			this.path = path
			parentPath = path.substringBeforeLast('/', "")
			name = path.substringAfterLast('/')
			this.mimeType = mimeType
		}
	}

	/**
	 * The provider and the app have to share the caches, which they only do if both get them from the app component.
	 */
	@Test
	fun `only the module creates the caches`() {
		val projectDir = File(System.getProperty("user.dir")!!).parentFile!!
		val constructorCall = Regex("""(?<![\w.])(CloudTaskScheduler|DocumentMetadataCache|DocumentContentCache)\(""")
		val callers = listOf("data", "domain", "presentation", "util") //
			.flatMap { File(projectDir, "$it/src").listFiles()!!.filterNot { sourceSet -> sourceSet.name.startsWith("test") || sourceSet.name.startsWith("androidTest") } } //
			.flatMap { sourceSet -> sourceSet.walk().filter { it.extension == "kt" || it.extension == "java" } } //
			.filter { it.name != "DocumentsModule.java" && constructorCall.containsMatchIn(it.readText().lineSequence().filterNot { line -> line.trimStart().startsWith("class ") }.joinToString("\n")) } //
			.map { it.name }

		assertEquals(emptyList<String>(), callers)
	}
}
