package org.cryptomator.presentation.presenter

import android.content.Intent
import android.net.Uri
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.DownloadFile
import org.cryptomator.domain.usecases.cloud.DeleteNodesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadFilesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.OpenVaultFilesUseCase
import org.cryptomator.generator.Callback
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.R
import org.cryptomator.presentation.docprovider.documentUri
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.ImagePreviewFile
import org.cryptomator.presentation.model.ImagePreviewFilesStore
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.ui.activity.view.ImagePreviewView
import org.cryptomator.presentation.ui.dialog.ConfirmDeleteCloudNodeDialog
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.util.ShareFileHelper
import org.cryptomator.presentation.workflow.ActivityResult
import java.io.FileNotFoundException
import javax.inject.Inject
import timber.log.Timber

@PerView
class ImagePreviewPresenter @Inject constructor( //
	exceptionMappings: ExceptionHandlers,  //
	private val shareFileHelper: ShareFileHelper,  //
	private val contentResolverUtil: ContentResolverUtil,  //
	private val downloadFilesUseCase: DownloadFilesUseCase,  //
	private val openVaultFilesUseCase: OpenVaultFilesUseCase,  //
	private val deleteNodesUseCase: DeleteNodesUseCase,  //
	private val fileUtil: FileUtil
) : Presenter<ImagePreviewView>(exceptionMappings) {

	private var isSystemUiVisible = true

	private val missingImagePreviewFiles = SerialRequests<List<ImagePreviewFile>>({ waiting, next -> waiting + next }, ::openImagePreviewFiles)

	@InstanceState
	lateinit var pageIndexes: ArrayList<Int>

	fun exportImageToUserSelectedLocation(image: CloudFileModel) {
		val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
		intent.addCategory(Intent.CATEGORY_OPENABLE)
		intent.type = "*/*"
		intent.putExtra(Intent.EXTRA_TITLE, image.name)
		requestActivityResult(ActivityResultCallbacks.exportImageToUserSelectedLocation(image), intent)
	}

	@Callback
	fun exportImageToUserSelectedLocation(result: ActivityResult, image: CloudFileModel) {
		try {
			val downloadFile = DownloadFile.Builder() //
				.setDownloadFile(image.toCloudNode()) //
				.setDataSink(contentResolverUtil.openOutputStream(Uri.parse(result.intent()?.dataString))) //
				.build()
			downloadFilesUseCase //
				.withDownloadFiles(listOf(downloadFile)) //
				.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, DownloadState>() {
					override fun onSuccess(files: List<CloudFile>) {
						view?.showMessage(R.string.screen_file_browser_msg_file_exported)
					}
				})
		} catch (e: FileNotFoundException) {
			showError(e)
		}
	}

	fun onShareImageClicked(image: CloudFileModel, uri: Uri) {
		shareFileHelper.shareFile(this, uri, image.name)
	}

	fun onDeleteImageClicked(imagePreviewFile: ImagePreviewFile) {
		view?.showDialog(ConfirmDeleteCloudNodeDialog.newInstance(listOf(imagePreviewFile.cloudFileModel)))
	}

	fun onDeleteImageConfirmed(imagePreviewFile: ImagePreviewFile, index: Int) {
		view?.showProgress(ProgressModel.GENERIC)
		deleteNodesUseCase
			.withCloudNodes(listOf(imagePreviewFile.cloudFileModel.toCloudNode()))
			.run(object : ProgressCompletingResultHandler<List<CloudNode?>?>() {
				override fun onFinished() {
					view?.showProgress(ProgressModel.COMPLETED)
					view?.onImageDeleted(index)
				}

				override fun onError(e: Throwable) {
					Timber.tag("ImagePreviewPresenter").e(e, "Failed to delete preview image")
					view?.showProgress(ProgressModel.COMPLETED)
				}
			})
	}

	fun onImagePreviewClicked() {
		if (isSystemUiVisible) {
			view?.hideSystemUi()
		} else {
			view?.showSystemUi()
		}
		isSystemUiVisible = !isSystemUiVisible
	}

	/**
	 * The pages next to the shown one ask for their images too, so the images are opened one after another.
	 */
	fun onMissingImagePreviewFile(imagePreviewFile: ImagePreviewFile) {
		missingImagePreviewFiles.request(listOf(imagePreviewFile))
	}

	private fun openImagePreviewFiles(imagePreviewFiles: List<ImagePreviewFile>, done: () -> Unit) {
		val imagePreviewFile = imagePreviewFiles.first()
		openVaultFilesUseCase //
			.withFiles(listOf(imagePreviewFile.cloudFileModel.toCloudNode())) //
			.run(object : DefaultProgressAwareResultHandler<List<String>, DownloadState>() {
				override fun onSuccess(documentIds: List<String>) {
					imagePreviewFile.uri = documentUri(documentIds[0])
					view?.showImagePreview(imagePreviewFile)
					view?.hideProgressBar(imagePreviewFile)
				}

				override fun onFinished() {
					if (imagePreviewFiles.size > 1) {
						openImagePreviewFiles(imagePreviewFiles.drop(1), done)
					} else {
						done()
					}
				}
			})
	}

	fun getImagePreviewFileStore(path: String): ImagePreviewFilesStore {
		return fileUtil.getImagePreviewFiles(path)
	}

	fun getImagePreviewFiles(imagePreviewFileStore: ImagePreviewFilesStore): ArrayList<ImagePreviewFile> {
		return imagePreviewFileStore.cloudFileModels.mapTo(ArrayList()) { ImagePreviewFile(it, null) }
	}

	init {
		unsubscribeOnDestroy(downloadFilesUseCase, openVaultFilesUseCase, deleteNodesUseCase)
	}
}
