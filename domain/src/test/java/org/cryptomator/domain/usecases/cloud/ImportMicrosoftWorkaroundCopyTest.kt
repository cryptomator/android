package org.cryptomator.domain.usecases.cloud

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.cryptomator.domain.exception.FatalBackendException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class ImportMicrosoftWorkaroundCopyTest {

	@TempDir
	lateinit var directory: File

	private val document: Uri = mock()
	private val contentResolver: ContentResolver = mock()
	private val context: Context = mock {
		on { contentResolver } doReturn contentResolver
	}
	private val descriptor: ParcelFileDescriptor = mock()
	private lateinit var folder: File
	private lateinit var copy: File
	private lateinit var hashWhenOpened: ByteArray

	/**
	 * What the provider got as the document's new content.
	 */
	private lateinit var saved: File
	private lateinit var savedStream: FileOutputStream

	@BeforeEach
	fun setUp() {
		folder = File(directory, "copy-folder").apply { mkdirs() }
		copy = File(folder, "Budget.xlsx").apply { writeText("opened") }
		hashWhenOpened = MicrosoftWorkaroundCopies.hash(copy)
		saved = File(directory, "document")
		savedStream = FileOutputStream(saved)
		whenever(descriptor.fileDescriptor).thenReturn(savedStream.fd)
		whenever(contentResolver.openFileDescriptor(document, "wt")).thenReturn(descriptor)
	}

	@AfterEach
	fun tearDown() {
		savedStream.close()
	}

	private fun inTest() = ImportMicrosoftWorkaroundCopy(context, copy, hashWhenOpened, document)

	@Test
	fun `an unchanged copy is deleted with its folder, without saving it`() {
		inTest().execute()

		assertFalse(folder.exists())
		verify(contentResolver, never()).openFileDescriptor(any(), any())
	}

	@Test
	fun `a changed copy replaces the content of the document and is deleted only once the document is closed`() {
		copy.writeText("saved by the editor")
		doAnswer { assertTrue(copy.exists()) }.whenever(descriptor).close()

		inTest().execute()

		assertEquals("saved by the editor", saved.readText())
		verify(descriptor).close()
		verify(descriptor, never()).closeWithError(any())
		assertFalse(folder.exists())
	}

	@Test
	fun `a save that fails half way is reported to the provider, which then drops it, and the copy is kept`() {
		copy.writeText("saved by the editor")
		// writing to the document fails
		savedStream.close()

		assertThrows<FatalBackendException> { inTest().execute() }

		verify(descriptor).closeWithError(any())
		verify(descriptor, never()).close()
		assertEquals("saved by the editor", copy.readText())
	}

	@Test
	fun `a copy that is gone once the document is open is reported to the provider, without its name`() {
		copy.writeText("saved by the editor")
		whenever(contentResolver.openFileDescriptor(document, "wt")).thenAnswer {
			copy.delete()
			descriptor
		}

		val failed = assertThrows<FatalBackendException> { inTest().execute() }

		assertFalse(failed.cause!!.message!!.contains("Budget"))
		verify(descriptor).closeWithError(any())
		verify(descriptor, never()).close()
	}

	@Test
	fun `a changed copy whose document can't be opened is kept, and not reported as gone`() {
		copy.writeText("saved by the editor")
		whenever(contentResolver.openFileDescriptor(document, "wt")).thenThrow(FileNotFoundException("Opening 1:4 failed"))

		assertThrows<FatalBackendException> { inTest().execute() }

		assertEquals("saved by the editor", copy.readText())
	}

	@Test
	fun `a changed copy whose document the provider doesn't open is kept`() {
		copy.writeText("saved by the editor")
		whenever(contentResolver.openFileDescriptor(document, "wt")).thenReturn(null)

		assertThrows<FatalBackendException> { inTest().execute() }

		assertEquals("saved by the editor", copy.readText())
	}

	@Test
	fun `a copy that is gone is reported as such, without its name`() {
		copy.delete()

		val gone = assertThrows<FileNotFoundException> { inTest().execute() }

		assertFalse(gone.message!!.contains("Budget"))
		assertTrue(gone.message!!.contains("copy-folder"))
	}

	@Test
	fun `the copy is looked at only after the editor had time to finish writing it`() {
		val started = System.nanoTime()
		// an editor that is still writing when the user is back
		copy = object : File(folder, "Budget.xlsx") {
			override fun exists(): Boolean {
				assertTrue((System.nanoTime() - started) / 1_000_000 >= ImportMicrosoftWorkaroundCopy.SETTLE_MILLIS)
				writeText("saved late")
				return super.exists()
			}
		}

		inTest().execute()

		assertEquals("saved late", saved.readText())
	}
}
