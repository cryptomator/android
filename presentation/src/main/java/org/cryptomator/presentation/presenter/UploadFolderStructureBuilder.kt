package org.cryptomator.presentation.presenter

import androidx.documentfile.provider.DocumentFile
import org.cryptomator.domain.usecases.cloud.UploadFile
import org.cryptomator.domain.usecases.cloud.UploadFolderStructure

fun buildUploadFolderStructure(documentFile: DocumentFile): UploadFolderStructure {
	val structure = UploadFolderStructure(documentFile.name ?: "folder")
	documentFile.listFiles().forEach { child ->
		when {
			child.isDirectory -> {
				structure.addSubfolder(buildUploadFolderStructure(child))
			}
			child.isFile -> {
				child.name?.let { name ->
					structure.addFile(
						UploadFile.anUploadFile() //
							.withFileName(name) //
							.withDataSource(UriBasedDataSource.from(child.uri)) //
							.thatIsReplacing(false) //
							.build()
					)
				}
			}
		}
	}
	return structure
}
