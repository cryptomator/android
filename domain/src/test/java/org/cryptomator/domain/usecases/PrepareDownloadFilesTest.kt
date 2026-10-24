package org.cryptomator.domain.usecases

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.exception.IllegalFileNameException
import org.cryptomator.util.file.MimeTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.OutputStream

class PrepareDownloadFilesTest {

	private val resolver: ContentResolver = mock()
	private val context: Context = mock { on { contentResolver } doReturn resolver }
	private val parent: Uri = mock()
	private val a = file("a.txt")
	private val b = file("b.txt")

	@Test
	fun `the targets are created when the export is prepared, but each is opened only when its file is downloaded`() {
		val targetA: Uri = mock()
		val targetB: Uri = mock()
		val sinkA: OutputStream = mock()
		val sinkB: OutputStream = mock()
		whenever(resolver.openOutputStream(targetA)).thenReturn(sinkA)
		whenever(resolver.openOutputStream(targetB)).thenReturn(sinkB)

		val downloads = mockStatic(DocumentsContract::class.java).use { contract ->
			contract.`when`<Uri> { DocumentsContract.createDocument(eq(resolver), eq(parent), anyString(), eq("a.txt")) }.thenReturn(targetA)
			contract.`when`<Uri> { DocumentsContract.createDocument(eq(resolver), eq(parent), anyString(), eq("b.txt")) }.thenReturn(targetB)
			prepare(listOf(a, b)).execute()
		}

		assertEquals(listOf(a, b), downloads.map { it.downloadFile })
		verify(resolver, never()).openOutputStream(any<Uri>())
		assertSame(sinkA, downloads[0].openDataSink())
		assertSame(sinkB, downloads[1].openDataSink())
	}

	@Test
	fun `a target that can't be created stops the export before any target is opened`() {
		mockStatic(DocumentsContract::class.java).use { contract ->
			contract.`when`<Uri> { DocumentsContract.createDocument(eq(resolver), eq(parent), anyString(), eq("a.txt")) }.thenReturn(mock<Uri>())
			contract.`when`<Uri> { DocumentsContract.createDocument(eq(resolver), eq(parent), anyString(), eq("b.txt")) }.thenReturn(null)

			assertThrows<IllegalFileNameException> { prepare(listOf(a, b)).execute() }
		}

		verify(resolver, never()).openOutputStream(any<Uri>())
	}

	private fun prepare(files: List<CloudFile>): PrepareDownloadFiles {
		return PrepareDownloadFiles(context, mock<MimeTypes>(), files, parent, CloudNodeRecursiveListing(0))
	}

	private fun file(name: String): CloudFile {
		return mock { on { this.name } doReturn name }
	}
}
