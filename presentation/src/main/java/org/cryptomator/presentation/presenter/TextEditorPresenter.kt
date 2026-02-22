package org.cryptomator.presentation.presenter

import org.cryptomator.domain.di.PerView
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.usecases.cloud.UploadFile
import org.cryptomator.generator.InstanceState
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.ui.activity.view.TextEditorView
import org.cryptomator.presentation.util.ContentResolverUtil
import org.cryptomator.presentation.util.FileUtil
import org.cryptomator.util.file.FileCacheUtils
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@PerView
class TextEditorPresenter @Inject constructor( //
	private val fileCacheUtils: FileCacheUtils,  //
	private val fileUtil: FileUtil,  //
	private val contentResolverUtil: ContentResolverUtil,  //
	exceptionMappings: ExceptionHandlers
) : Presenter<TextEditorView>(exceptionMappings) {

	private val cryptomatorApp: CryptomatorApp get() = activity().application as CryptomatorApp
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
		view?.let { v ->
			val file = textFile.get()
			val parent = file.parent ?: throw ParentFolderIsNullException(file.name)
			val parentNode = parent.toCloudNode()
			val cloud = parentNode.cloud ?: return
			val vaultId = parent.vault()?.toVault()?.id ?: return

			val uri = fileCacheUtils.tmpFile() //
				.withContent(v.textFileContent) //
				.create()
			val files = listOf(
				UploadFile.anUploadFile() //
					.withFileName(file.name) //
					.withDataSource(UriBasedDataSource.from(uri)) //
					.thatIsReplacing(true) //
					.build()
			)

			if (cryptomatorApp.startFileUpload(cloud, parentNode.path, files, emptySet(), vaultId, listOf(uri))) {
				existingTextFileContent.set(v.textFileContent)
				v.showMessage(R.string.notification_upload_started)
				v.finish()
			} else {
				uri.path?.let { File(it).delete() }
				v.showMessage(R.string.error_upload_service_unavailable)
			}
		}
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
}
