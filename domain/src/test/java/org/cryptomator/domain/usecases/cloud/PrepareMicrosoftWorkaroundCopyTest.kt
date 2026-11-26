package org.cryptomator.domain.usecases.cloud

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import org.cryptomator.domain.exception.FatalBackendException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class PrepareMicrosoftWorkaroundCopyTest {

	@TempDir
	lateinit var directory: File

	private val document: Uri = mock()
	private val contentResolver: ContentResolver = mock()
	private val context: Context = mock {
		on { contentResolver } doReturn contentResolver
	}
	private lateinit var folder: File
	private lateinit var target: File

	@BeforeEach
	fun setUp() {
		folder = File(directory, "copy-folder").apply { mkdirs() }
		target = File(folder, "Budget.xlsx")
	}

	@Test
	fun `the document is copied and the hash of the copy returned`() {
		// more than one buffer
		val content = ByteArray(20_000) { (it % 251).toByte() }
		var closed = false
		whenever(contentResolver.openInputStream(document)).thenReturn(object : ByteArrayInputStream(content) {
			override fun close() {
				closed = true
			}
		})

		val hash = PrepareMicrosoftWorkaroundCopy(context, document, target).execute()

		// the provider keeps the document in use until it is closed
		assertTrue(closed)
		assertArrayEquals(content, target.readBytes())
		assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(content), hash)
		assertArrayEquals(hash, MicrosoftWorkaroundCopies.hash(target))
	}

	@Test
	fun `a copy that fails half way leaves nothing of the document behind`() {
		var closed = false
		val failing = object : InputStream() {
			private var served = 0
			override fun close() {
				closed = true
			}

			override fun read(): Int = throw UnsupportedOperationException()
			override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
				if (served++ > 0) {
					throw IOException("gone")
				}
				buffer.fill(1, offset, offset + length)
				return length
			}
		}
		whenever(contentResolver.openInputStream(document)).thenReturn(failing)

		assertThrows<FatalBackendException> { PrepareMicrosoftWorkaroundCopy(context, document, target).execute() }

		assertTrue(closed)
		assertFalse(target.exists())
		assertFalse(folder.exists())
	}

	@Test
	fun `a copy that can't be created fails without its name and leaves no folder behind`() {
		// a file can't be created in place of a folder
		target.mkdirs()
		whenever(contentResolver.openInputStream(document)).thenReturn(ByteArrayInputStream(ByteArray(1)))

		val failed = assertThrows<FatalBackendException> { PrepareMicrosoftWorkaroundCopy(context, document, target).execute() }

		assertFalse(failed.cause!!.message!!.contains("Budget"))
		assertFalse(folder.exists())
	}

	@Test
	fun `a document that can't be opened fails the copy and leaves no folder behind`() {
		whenever(contentResolver.openInputStream(document)).thenReturn(null)

		assertThrows<FatalBackendException> { PrepareMicrosoftWorkaroundCopy(context, document, target).execute() }

		assertFalse(folder.exists())
	}
}
