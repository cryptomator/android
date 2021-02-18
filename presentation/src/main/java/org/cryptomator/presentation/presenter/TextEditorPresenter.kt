package org.cryptomator.presentation.presenter

import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.UploadFile
import org.cryptomator.domain.usecases.cloud.UploadFilesUseCase
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.ProgressModel
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.file.FileCacheUtils
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@PerView
class TextEditorPresenter @Inject constructor( //
		private val fileCacheUtils: FileCacheUtils,  //
		private val fileUtil: FileUtil,  //
		private val contentResolverUtil: ContentResolverUtil,  //
		private val uploadFilesUseCase: UploadFilesUseCase,  //
		exceptionMappings: ExceptionHandlers) : Presenter<TextEditorView>(exceptionMappings) {

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
		uploadFilesUseCase //
				.withParent(textFile.get().parent.toCloudNode()) //
				.andFiles(listOf( //
						UploadFile.anUploadFile() //
								.withFileName(fileName) //
								.withDataSource(dataSource) //
								.thatIsReplacing(true) //
								.build() //
				)) //
				.run(object : DefaultProgressAwareResultHandler<List<CloudFile?>, UploadState>() {
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
	}

	fun loadFileContent() {
		// only load file content once since EditText retains its own instance state
		if (didLoadFileContent) {
			return
		}
		val textFileUri = fileUtil.contentUriFor(textFile.get())
		try {
			val data = contentResolverUtil.openInputStream(textFileUri)
			data?.let {
				existingTextFileContent.set(fileCacheUtils.read(it))
				view?.displayTextFileContent(existingTextFileContent.get())
				didLoadFileContent = true
			}
		} catch (e: IOException) {
			showError(e)
		}
	}

	fun setTextFile(textFile: CloudFileModel) {
		this.textFile.set(textFile)
	}

	init {
		unsubscribeOnDestroy(uploadFilesUseCase)
	}
}
