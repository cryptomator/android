package org.cryptomator.presentation.model

import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.usecases.ResultRenamed
import org.cryptomator.presentation.util.FileIcon
import org.cryptomator.util.Optional
import java.util.Date

class CloudFileModel(cloudFile: CloudFile, val icon: FileIcon) : CloudNodeModel<CloudFile>(cloudFile) {

	val modified: Optional<Date> = cloudFile.modified
	val size: Optional<Long> = cloudFile.size

	constructor(cloudFileRenamed: ResultRenamed<CloudFile>, icon: FileIcon) : this(cloudFileRenamed.value(), icon) {
		oldName = cloudFileRenamed.oldName
	}

	override val isFile: Boolean
		get() = true
	override val isFolder: Boolean
		get() = false

	val isCryptomatorFile: Boolean
		get() = name.endsWith(".cryptomator")

}
