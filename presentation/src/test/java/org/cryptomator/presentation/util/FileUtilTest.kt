package org.cryptomator.presentation.util

import android.content.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

class FileUtilTest {

	@TempDir
	lateinit var directory: File

	@Test
	fun `cleaning up deletes what earlier versions left behind, and nothing else that is recent`() {
		val cacheDir = File(directory, "cache")
		val mediaDir = File(directory, "media")
		listOf("LruCacheDropbox/a", "LruCache-1/b", "decrypted/recent.txt", "thumbnails/thumbnail.tmp", "LruCacheFile").forEach { File(cacheDir, it).apply { parentFile?.mkdirs() }.writeText("x") }
		File(mediaDir, "decrypted/report.docx").apply { parentFile?.mkdirs() }.writeText("plaintext")
		File(mediaDir, "other.jpg").writeText("x")
		val context: Context = mock {
			on { this.cacheDir } doReturn cacheDir
			on { externalMediaDirs } doReturn arrayOf(mediaDir)
		}

		FileUtil(context, mock()).cleanup()

		assertEquals(setOf("LruCacheFile", "decrypted", "thumbnails"), cacheDir.list()!!.toSet())
		assertEquals(listOf("recent.txt"), File(cacheDir, "decrypted").list()!!.toList())
		assertFalse(File(mediaDir, "decrypted").exists())
		assertEquals(listOf("other.jpg"), mediaDir.list()!!.toList())
	}

	@Test
	fun `cleaning up deletes the shared media folder that asking for it created, unless something is in it`() {
		val cacheDir = File(directory, "cache").apply { mkdirs() }
		val emptyMediaDir = File(directory, "media").apply { mkdirs() }
		val usedMediaDir = File(directory, "sdcard-media").apply { mkdirs() }
		File(usedMediaDir, "photo.jpg").writeText("x")
		val context: Context = mock {
			on { this.cacheDir } doReturn cacheDir
			on { externalMediaDirs } doReturn arrayOf(emptyMediaDir, usedMediaDir)
		}

		FileUtil(context, mock()).cleanup()

		assertFalse(emptyMediaDir.exists())
		assertEquals(listOf("photo.jpg"), usedMediaDir.list()!!.toList())
	}
}
