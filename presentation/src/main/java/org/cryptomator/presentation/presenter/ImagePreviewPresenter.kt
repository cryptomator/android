package org.cryptomator.presentation.presenter

import android.Manifest
import android.content.Intent
import android.net.Uri
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.usecases.CopyDataUseCase
import org.cryptomator.domain.usecases.cloud.DeleteNodesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadFilesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.generator.Callback
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.ImagePreviewFile
import org.cryptomator.presentation.model.ImagePreviewFilesStore
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.model.mappers.CloudFileModelMapper
import org.cryptomator.presentation.ui.activity.view.ImagePreviewView
import org.cryptomator.presentation.ui.dialog.ConfirmDeleteCloudNodeDialog
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.DownloadFileUtil
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.presentation.util.ShareFileHelper
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.PermissionsResult
import org.cryptomator.util.ExceptionUtil
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayList
import javax.inject.Inject
import timber.log.Timber

@PerView
class ImagePreviewPresenter @Inject constructor( //
	exceptionMappings: ExceptionHandlers,  //
	private val shareFileHelper: ShareFileHelper,  //
	private val contentResolverUtil: ContentResolverUtil,  //
	private val copyDataUseCase: CopyDataUseCase,  //
	private val downloadFilesUseCase: DownloadFilesUseCase,  //
	private val deleteNodesUseCase: DeleteNodesUseCase,  //
	private val downloadFileUtil: DownloadFileUtil,  //
	private val fileUtil: FileUtil,  //
	private val cloudFileModelMapper: CloudFileModelMapper
) : Presenter<ImagePreviewView>(exceptionMappings) {

	private var isSystemUiVisible = true

	@InstanceState
	lateinit var pageIndexes: ArrayList<Int>

	fun exportImageToUserSelectedLocation(uri: Uri) {
		val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
		intent.addCategory(Intent.CATEGORY_OPENABLE)
		intent.type = "*/*"
		intent.putExtra(Intent.EXTRA_TITLE, contentResolverUtil.fileName(uri))
		requestActivityResult(ActivityResultCallbacks.exportImageToUserSelectedLocation(uri.toString()), intent)
	}

	@Callback
	fun exportImageToUserSelectedLocation(result: ActivityResult, sourceUri: String?) {
		requestPermissions(
			PermissionsResultCallbacks.exportImageToUserSelectedLocation(result.intent()?.dataString, sourceUri),  //
			R.string.permission_message_export_file,  //
			Manifest.permission.READ_EXTERNAL_STORAGE
		)
	}

	@Callback
	fun exportImageToUserSelectedLocation(result: PermissionsResult, targetUri: String?, sourceUri: String?) {
		if (result.granted()) {
			try {
				copyFile(
					contentResolverUtil.openInputStream(Uri.parse(sourceUri)),  //
					contentResolverUtil.openOutputStream(Uri.parse(targetUri))
				)
			} catch (e: FileNotFoundException) {
				showError(e)
			}
		}
	}

	private fun copyFile(source: InputStream?, target: OutputStream?) {
		if (source == null || target == null) {
			throw FatalBackendException("Input- or OutputStream is null")
		}
		copyDataUseCase //
			.withSource(source) //
			.andTarget(target) //
			.run(object : DefaultResultHandler<Void?>() {
				override fun onFinished() {
					view?.showMessage(R.string.screen_file_browser_msg_file_exported)
				}
			})
	}

	fun onShareImageClicked(uri: Uri) {
		shareFileHelper.shareFile(this, uri)
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

	fun onMissingImagePreviewFile(imagePreviewFile: ImagePreviewFile) {
		downloadFilesUseCase //
			.withDownloadFiles(downloadFileUtil.createDownloadFilesFor(this, listOf(imagePreviewFile.cloudFileModel))) //
			.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, DownloadState>() {
				override fun onSuccess(result: List<CloudFile>) {
					cloudFileModelMapper.toModel(result[0])
					imagePreviewFile.uri = fileUtil.contentUriFor(cloudFileModelMapper.toModel(result[0]))
					view?.showImagePreview(imagePreviewFile)
					view?.hideProgressBar(imagePreviewFile)
				}

				override fun onError(e: Throwable) {
					if (ExceptionUtil.contains(e, IOException::class.java, ExceptionUtil.thatContainsMessage("Stream Closed"))) {
						// ignore error
						Timber.tag("ImagePreviewPresenter").d("User swiped to quickly and close the stream before finishing the download.")
					} else {
						super.onError(e)
					}
				}
			})
	}

	fun getImagePreviewFileStore(path: String): ImagePreviewFilesStore {
		return fileUtil.getImagePreviewFiles(path)
	}

	fun getImagePreviewFiles(imagePreviewFileStore: ImagePreviewFilesStore, index: Int): ArrayList<ImagePreviewFile> {
		val imagePreviewFiles = imagePreviewFileStore.cloudFileModels.mapTo(ArrayList<ImagePreviewFile>()) { ImagePreviewFile(it, null) }

		// first file is already downloaded
		val cloudFileModel = imagePreviewFileStore.cloudFileModels[index]
		imagePreviewFiles[index] = ImagePreviewFile(cloudFileModel, fileUtil.contentUriFor(cloudFileModel))
		return imagePreviewFiles
	}

	init {
		unsubscribeOnDestroy(copyDataUseCase, downloadFilesUseCase, deleteNodesUseCase)
	}
}
