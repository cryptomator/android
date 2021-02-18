package org.cryptomator.presentation.model.mappers

import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.ProgressState
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.presentation.R
import org.cryptomator.presentation.model.FileProgressStateModel
import org.cryptomator.presentation.model.ProgressStateModel
import org.cryptomator.presentation.util.FileIcon
import org.cryptomator.presentation.util.FileUtil
import javax.inject.Inject

class ProgressStateModelMapper @Inject internal constructor(private val fileUtil: FileUtil) : ModelMapper<ProgressStateModel, ProgressState?>() {

	/**
	 * @throws IllegalStateException
	 */
	@Deprecated("Not implemented")
	override fun fromModel(model: ProgressStateModel): ProgressState? {
		throw IllegalStateException("Not implemented")
	}

	override fun toModel(domainObject: ProgressState?): ProgressStateModel {
		if (domainObject is UploadState) {
			return toModel(domainObject)
		} else if (domainObject is DownloadState) {
			return toModel(domainObject)
		}
		return ProgressStateModel.COMPLETED
	}

	fun toModel(state: UploadState): ProgressStateModel {
		return if (state.isUpload) {
			FileProgressStateModel(state.file(), FileIcon.fileIconFor(state.file().name, fileUtil), FileProgressStateModel.UPLOAD, ProgressStateModel.image(R.drawable.ic_file_upload),
					ProgressStateModel.text(R.string.dialog_progress_upload_file))
		} else {
			FileProgressStateModel(state.file(), FileIcon.fileIconFor(state.file().name, fileUtil), FileProgressStateModel.ENCRYPTION, ProgressStateModel.image(R.drawable.ic_lock_closed),
					ProgressStateModel.text(R.string.dialog_progress_encryption))
		}
	}

	fun toModel(state: DownloadState): ProgressStateModel {
		return if (state.isDownload) {
			FileProgressStateModel(state.file(), FileIcon.fileIconFor(state.file().name, fileUtil), FileProgressStateModel.DOWNLOAD, ProgressStateModel.image(R.drawable.ic_file_download),
					ProgressStateModel.text(R.string.dialog_progress_download_file))
		} else {
			FileProgressStateModel(state.file(), FileIcon.fileIconFor(state.file().name, fileUtil), FileProgressStateModel.DECRYPTION, ProgressStateModel.image(R.drawable.ic_lock_open),
					ProgressStateModel.text(R.string.dialog_progress_decryption))
		}
	}
}
