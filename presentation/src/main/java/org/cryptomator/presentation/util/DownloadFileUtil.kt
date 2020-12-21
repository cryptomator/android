package org.cryptomator.presentation.util

import org.cryptomator.domain.usecases.DownloadFile
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.presenter.Presenter
import java.io.FileNotFoundException
import java.io.OutputStream
import java.util.*
import javax.inject.Inject

class DownloadFileUtil @Inject constructor(private val fileUtil: FileUtil) {

	fun createDownloadFilesFor(presenter: Presenter<*>, cloudFiles: List<CloudFileModel>): List<DownloadFile> {
		return cloudFiles.mapTo(ArrayList()) {
			DownloadFile.Builder() //
					.setDownloadFile(it.toCloudNode()) //
					.setDataSink(createDecryptedDataFor(presenter, it)) //
					.build()
		}
	}

	fun createDecryptedDataFor(presenter: Presenter<*>, cloudFile: CloudFileModel): OutputStream? {
		try {
			return fileUtil.newDecryptedData(cloudFile)
		} catch (e: FileNotFoundException) {
			presenter.showError(e)
		}
		return null
	}
}
