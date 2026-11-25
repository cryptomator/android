package org.cryptomator.presentation.util

import android.content.Context
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

class FileUtilTest {

	@TempDir
	lateinit var directory: File

	@Test
	fun `cleaning up deletes the caches earlier versions left behind and Microsoft workaround copies older than five hours, and nothing more recent`() {
		val cacheDir = File(directory, "cache")
		val mediaDir = File(directory, "media")
		listOf("LruCacheDropbox/a", "LruCache-1/b", "decrypted/recent.txt", "thumbnails/thumbnail.tmp", "LruCacheFile").forEach { File(cacheDir, it).apply { parentFile?.mkdirs() }.writeText("x") }
		microsoftWorkaroundCopy(mediaDir, "abandoned/a.docx", ageMinutes = 5 * 60 + 1)
		microsoftWorkaroundCopy(mediaDir, "left-by-an-earlier-version.docx", ageMinutes = 6 * 60)
		microsoftWorkaroundCopy(mediaDir, "edited-earlier-today/b.xlsx", ageMinutes = 4 * 60)
		microsoftWorkaroundCopy(mediaDir, "edited-right-now/c.xlsx", ageMinutes = 0)
		File(mediaDir, "other.jpg").writeText("x")
		val context: Context = mock {
			on { this.cacheDir } doReturn cacheDir
			on { externalMediaDirs } doReturn arrayOf(mediaDir)
		}

		FileUtil(context, mock()).cleanup()

		assertEquals(setOf("LruCacheFile", "decrypted", "thumbnails"), cacheDir.list()!!.toSet())
		assertEquals(listOf("recent.txt"), File(cacheDir, "decrypted").list()!!.toList())
		assertEquals(setOf("edited-earlier-today", "edited-right-now"), File(mediaDir, "decrypted").list()!!.toSet())
		assertTrue(File(mediaDir, "decrypted/edited-earlier-today/b.xlsx").exists())
		assertTrue(File(mediaDir, "decrypted/edited-right-now/c.xlsx").exists())
		assertEquals(setOf("decrypted", "other.jpg"), mediaDir.list()!!.toSet())
	}

	@Test
	fun `cleaning up deletes the Microsoft workaround folder once its last copy is old`() {
		val cacheDir = File(directory, "cache").apply { mkdirs() }
		val mediaDir = File(directory, "media")
		microsoftWorkaroundCopy(mediaDir, "abandoned/a.docx", ageMinutes = 6 * 60)
		val context: Context = mock {
			on { this.cacheDir } doReturn cacheDir
			on { externalMediaDirs } doReturn arrayOf(mediaDir)
		}

		FileUtil(context, mock()).cleanup()

		assertFalse(mediaDir.exists())
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

	@Test
	fun `a Microsoft workaround copy goes into a folder of its own in the primary media folder, with a lowercase extension`() {
		val mediaDir = File(directory, "media").apply { mkdirs() }
		val context: Context = mock {
			on { externalMediaDirs } doReturn arrayOf(mediaDir, File(directory, "sdcard-media").apply { mkdirs() })
		}
		val inTest = FileUtil(context, mock())

		val copy = inTest.microsoftWorkaroundCopyFor("Annual.Report.DOCX")!!
		val nextCopy = inTest.microsoftWorkaroundCopyFor("Annual.Report.DOCX")!!

		assertEquals("Annual.Report.docx", copy.name)
		assertEquals(File(mediaDir, "decrypted"), copy.parentFile!!.parentFile)
		assertTrue(copy.parentFile!!.isDirectory)
		assertFalse(copy.exists())
		assertNotEquals(copy, nextCopy)
	}

	@Test
	fun `there is no Microsoft workaround copy without the primary media folder, also if another storage has one`() {
		val context: Context = mock {
			on { externalMediaDirs } doReturn arrayOf(null, File(directory, "sdcard-media").apply { mkdirs() })
		}

		assertNull(FileUtil(context, mock()).microsoftWorkaroundCopyFor("Budget.xlsx"))
	}

	@Test
	fun `there is no Microsoft workaround copy if the primary media folder can't be written`() {
		val mediaDir: File = mock {
			on { canWrite() } doReturn false
		}
		val context: Context = mock {
			on { externalMediaDirs } doReturn arrayOf(mediaDir)
		}

		assertNull(FileUtil(context, mock()).microsoftWorkaroundCopyFor("Budget.xlsx"))
	}

	private fun microsoftWorkaroundCopy(mediaDir: File, name: String, ageMinutes: Int) {
		File(mediaDir, "decrypted/$name").apply { parentFile?.mkdirs() }.apply { writeText("plaintext") }.setLastModified(System.currentTimeMillis() - ageMinutes * 60_000L)
	}
}
