package org.cryptomator.presentation.presenter

import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.usecases.DownloadFile
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadFilesUseCase
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadFile
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.util.file.FileCacheUtils
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@PerView
class TextEditorPresenter @Inject constructor( //
	private val fileCacheUtils: FileCacheUtils,  //
	private val downloadFilesUseCase: DownloadFilesUseCase,  //
	private val uploadFilesUseCase: UploadFilesUseCase,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<TextEditorView>(exceptionMappings) {

	private val textFile = AtomicReference<CloudFileModel>()

	@JvmField
	@InstanceState
	var existingTextFileContent = AtomicReference("")

	@JvmField
	@InstanceState
	var didLoadFileContent = false

	@JvmField
	@InstanceState
	var lastFilterLocation = 0

	@JvmField
	@InstanceState
	var query: String? = null

	fun onBackPressed() {
		if (hasUnsavedChanges()) {
			view?.showUnsavedChangesDialog()
		} else {
			view?.performBackPressed()
		}
	}

	private fun hasUnsavedChanges(): Boolean {
		return existingTextFileContent.get() != view?.textFileContent
	}

	fun saveChanges() {
		if (!hasUnsavedChanges()) {
			return
		}
		view?.let {
			it.showProgress(ProgressModel.GENERIC)
			val uri = fileCacheUtils.tmpFile() //
				.withContent(it.textFileContent) //
				.create()
			uploadFile(textFile.get().name, UriBasedDataSource.from(uri))
		}
	}

	private fun uploadFile(fileName: String, dataSource: DataSource) {
		textFile.get().parent?.let {
			uploadFilesUseCase //
				.withParent(it.toCloudNode()) //
				.andFiles(
					listOf( //
						UploadFile.anUploadFile() //
							.withFileName(fileName) //
							.withDataSource(dataSource) //
							.thatIsReplacing(true) //
							.build() //
					)
				).run(object : DefaultProgressAwareResultHandler<List<CloudFile?>, UploadState>() {
					override fun onFinished() {
						view?.showProgress(ProgressModel.COMPLETED)
						view?.finish()
						view?.showMessage(R.string.screen_text_editor_save_success)
					}

					override fun onError(e: Throwable) {
						view?.showProgress(ProgressModel.COMPLETED)
						showError(e)
					}
				})
		} ?: throw ParentFolderIsNullException(textFile.get().name)
	}

	fun loadFileContent() {
		// only load file content once since EditText retains its own instance state
		if (didLoadFileContent) {
			return
		}
		view?.showProgress(ProgressModel.GENERIC)
		val data = ByteArrayOutputStream()
		downloadFilesUseCase //
			.withDownloadFiles(listOf(DownloadFile.Builder().setDownloadFile(textFile.get().toCloudNode()).setDataSink(data).build())) //
			.run(object : DefaultProgressAwareResultHandler<List<CloudFile>, DownloadState>() {
				override fun onSuccess(files: List<CloudFile>) {
					existingTextFileContent.set(fileCacheUtils.read(ByteArrayInputStream(data.toByteArray())))
					view?.displayTextFileContent(existingTextFileContent.get())
					didLoadFileContent = true
				}

				override fun onFinished() {
					view?.showProgress(ProgressModel.COMPLETED)
				}
			})
	}

	fun setTextFile(textFile: CloudFileModel) {
		this.textFile.set(textFile)
	}

	init {
		unsubscribeOnDestroy(downloadFilesUseCase, uploadFilesUseCase)
	}
}
