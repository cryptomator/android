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
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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

	private fun inTest() = inTestThatWaits().also { it.settleMillis = 0 }

	/**
	 * As the app runs it, giving the editor time to finish writing.
	 */
	private fun inTestThatWaits() = ImportMicrosoftWorkaroundCopy(context, copy, hashWhenOpened, document)

	private fun assertSettled(fromNanos: Long, toNanos: Long = System.nanoTime()) {
		assertTrue((toNanos - fromNanos) / 1_000_000 >= ImportMicrosoftWorkaroundCopy.SETTLE_MILLIS)
	}

	private fun assertNamesOnlyTheFolder(message: String?) {
		assertFalse(message!!.contains("Budget"))
		assertTrue(message.contains("copy-folder"))
	}

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
	fun `a copy the app writes while it is saved is saved again, and the provider is told to drop the first save`() {
		copy.writeText("saved by the editor")
		val savesAt = mutableListOf<Long>()
		whenever(contentResolver.openFileDescriptor(document, "wt")).thenAnswer {
			// the provider truncates the document
			savedStream.channel.truncate(0)
			savesAt += System.nanoTime()
			if (savesAt.size == 1) {
				// as long as before, so only its time tells that it was written
				copy.writeText("saved by the EDITOR")
			}
			descriptor
		}

		inTestThatWaits().execute()

		assertEquals("saved by the EDITOR", saved.readText())
		// the app got time to finish writing
		assertSettled(savesAt[0], savesAt[1])
		inOrder(descriptor) {
			verify(descriptor).closeWithError(any())
			verify(descriptor).close()
		}
		assertFalse(folder.exists())
	}

	@Test
	fun `a copy the app writes right after it was found unchanged is saved`() {
		var lengthsRead = 0
		copy = object : File(folder, "Budget.xlsx") {
			override fun length(): Long {
				// read before the copy is compared, and again after
				if (lengthsRead++ == 1) {
					// with the time of the previous write, so only its size tells that it was written
					val lastModified = lastModified()
					writeText("saved late")
					setLastModified(lastModified)
				}
				return super.length()
			}
		}

		inTest().execute()

		assertEquals("saved late", saved.readText())
		assertFalse(folder.exists())
	}

	@Test
	fun `a copy the app writes at every attempt to save it is kept and reported, without its name`() {
		copy.writeText("saved by the editor")
		whenever(contentResolver.openFileDescriptor(document, "wt")).thenAnswer {
			copy.appendText(".")
			descriptor
		}

		val failed = assertThrows<FatalBackendException> { inTest().execute() }

		assertNamesOnlyTheFolder(failed.message)
		verify(descriptor, times(ImportMicrosoftWorkaroundCopy.MAX_ATTEMPTS)).closeWithError(any())
		verify(descriptor, never()).close()
		assertTrue(copy.exists())
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

		assertNamesOnlyTheFolder(failed.cause!!.message)
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

		assertNamesOnlyTheFolder(gone.message)
	}

	@Test
	fun `the copy is looked at only after the editor had time to finish writing it`() {
		val started = System.nanoTime()
		// an editor that is still writing when the user is back
		copy = object : File(folder, "Budget.xlsx") {
			override fun exists(): Boolean {
				assertSettled(started)
				writeText("saved late")
				return super.exists()
			}
		}

		inTestThatWaits().execute()

		assertEquals("saved late", saved.readText())
	}
}
