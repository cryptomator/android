package org.cryptomator.presentation.model.mappers

import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.usecases.ResultRenamed
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.util.FileIcon
import org.cryptomator.presentation.util.FileUtil
import javax.inject.Inject

class CloudFileModelMapper @Inject constructor(private val fileUtil: FileUtil) : ModelMapper<CloudFileModel, CloudFile>() {

	override fun fromModel(model: CloudFileModel): CloudFile {
		return model.toCloudNode()
	}

	override fun toModel(domainObject: CloudFile): CloudFileModel {
		return CloudFileModel(domainObject, FileIcon.fileIconFor(domainObject.name, fileUtil))
	}

	fun toModel(resultRenamed: ResultRenamed<CloudFile>): CloudFileModel {
		return CloudFileModel(resultRenamed, FileIcon.fileIconFor(resultRenamed.value().name, fileUtil))
	}
}
