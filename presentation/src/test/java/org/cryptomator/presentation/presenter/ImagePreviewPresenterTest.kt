package org.cryptomator.presentation.presenter

import android.content.Intent
import android.net.Uri
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.usecases.DownloadFile
import org.cryptomator.domain.usecases.cloud.DownloadFilesUseCase
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.workflow.ActivityResult
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.OutputStream

class ImagePreviewPresenterTest {

	private val contentResolverUtil: ContentResolverUtil = mock()
	private val downloadFilesUseCase: DownloadFilesUseCase = mock()

	private val inTest = ImagePreviewPresenter(mock(), mock(), contentResolverUtil, downloadFilesUseCase, mock(), mock(), mock())

	@Test
	fun `an exported image's target is opened by its download, not where it was chosen`() {
		val target: Uri = mock()
		val sink: OutputStream = mock()
		val cloudFile: CloudFile = mock()
		val image: CloudFileModel = mock { on { toCloudNode() } doReturn cloudFile }
		val intent: Intent = mock { on { dataString } doReturn "content://target" }
		val result: ActivityResult = mock { on { intent() } doReturn intent }
		whenever(downloadFilesUseCase.withDownloadFiles(any())).thenReturn(mock())
		whenever(contentResolverUtil.openOutputStream(target)).thenReturn(sink)

		mockStatic(Uri::class.java).use { uris ->
			uris.`when`<Uri> { Uri.parse("content://target") }.thenReturn(target)
			inTest.exportImageToUserSelectedLocation(result, image)
		}

		val downloads = argumentCaptor<List<DownloadFile>>()
		verify(downloadFilesUseCase).withDownloadFiles(downloads.capture())
		verify(contentResolverUtil, never()).openOutputStream(any())
		val download = downloads.firstValue.single()
		assertSame(cloudFile, download.downloadFile)
		assertSame(sink, download.openDataSink())
	}
}
