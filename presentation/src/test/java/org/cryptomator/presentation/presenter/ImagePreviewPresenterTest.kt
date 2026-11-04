package org.cryptomator.presentation.presenter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.usecases.DownloadFile
import org.cryptomator.domain.usecases.ResultHandler
import org.cryptomator.domain.usecases.cloud.DeleteNodesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadFilesUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.CloudFolderModel
import org.cryptomator.presentation.model.ImagePreviewFile
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.ui.activity.view.ImagePreviewView
import org.cryptomator.presentation.ui.dialog.ChangeFailedDialog
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.workflow.ActivityResult
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.OutputStream

class ImagePreviewPresenterTest {

	private val contentResolverUtil: ContentResolverUtil = mock()
	private val downloadFilesUseCase: DownloadFilesUseCase = mock()
	private val deleteNodesUseCase: DeleteNodesUseCase = mock()
	private val exceptionHandlers: ExceptionHandlers = mock()
	private val deletion: DeleteNodesUseCase.Launcher = mock()

	init {
		whenever(deleteNodesUseCase.withCloudNodes(any())).thenReturn(deletion)
	}

	private val inTest = ImagePreviewPresenter(exceptionHandlers, mock(), contentResolverUtil, downloadFilesUseCase, mock(), deleteNodesUseCase, mock())

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

	@Test
	fun `an image's page goes once the cloud confirmed its deletion`() {
		val view = givenView()
		val image = givenImage(inVault = true)

		inTest.onDeleteImageConfirmed(ImagePreviewFile(image, null), 1)

		verify(view).showProgress(ProgressModel.GENERIC)
		verify(view, never()).showProgress(ProgressModel.COMPLETED)
		verify(view, never()).onImageDeleted(any())
		verify(deletion, never()).andOptimistic(any())

		result().onSuccess(listOf(image.toCloudNode()))

		verify(view).showProgress(ProgressModel.COMPLETED)
		verify(view).onImageDeleted(1)
	}

	@Test
	fun `an image of a vault whose deletion the cloud rejected stays, and the rejection is named like in the browser`() {
		val view = givenView()
		val image = givenImage(inVault = true)
		val rejection = NetworkConnectionException()

		inTest.onDeleteImageConfirmed(ImagePreviewFile(image, null), 1)
		mockConstruction(Bundle::class.java).use {
			result().onError(rejection)
		}
		result().onFinished()

		val inOrder = inOrder(view, exceptionHandlers)
		inOrder.verify(view).showProgress(ProgressModel.COMPLETED)
		inOrder.verify(view).showDialog(isA<ChangeFailedDialog>())
		inOrder.verify(exceptionHandlers).handle(view, rejection)
		verify(view, never()).onImageDeleted(any())
	}

	@Test
	fun `a failed deletion of an image outside a vault is shown as an error, like in the browser`() {
		val view = givenView()
		val failure = NetworkConnectionException()

		inTest.onDeleteImageConfirmed(ImagePreviewFile(givenImage(inVault = false), null), 1)
		result().onError(failure)
		result().onFinished()

		verify(view).showProgress(ProgressModel.COMPLETED)
		verify(exceptionHandlers).handle(view, failure)
		verify(view, never()).showDialog(any())
		verify(view, never()).onImageDeleted(any())
	}

	private fun givenView(): ImagePreviewView {
		val view: ImagePreviewView = mock()
		inTest.view = view
		return view
	}

	private fun givenImage(inVault: Boolean): CloudFileModel {
		val folder: CloudFolderModel = mock { on { vault() } doReturn (if (inVault) mock<VaultModel>() else null) }
		val node: CloudFile = mock()
		return mock {
			on { toCloudNode() } doReturn node
			on { name } doReturn "x.jpg"
			on { parent } doReturn folder
		}
	}

	private fun result(): ResultHandler<List<CloudNode?>?> {
		val handler = argumentCaptor<ResultHandler<List<CloudNode?>?>>()
		verify(deletion).run(handler.capture())
		return handler.firstValue
	}
}
