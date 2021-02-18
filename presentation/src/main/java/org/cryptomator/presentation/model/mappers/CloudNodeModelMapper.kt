package org.cryptomator.presentation.model.mappers

import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.usecases.ResultRenamed
import org.cryptomator.presentation.model.CloudNodeModel
import javax.inject.Inject

class CloudNodeModelMapper @Inject constructor(private val cloudFileModelMapper: CloudFileModelMapper, private val cloudFolderModelMapper: CloudFolderModelMapper) : ModelMapper<CloudNodeModel<*>, CloudNode>() {

	override fun fromModel(model: CloudNodeModel<*>): CloudNode {
		return model.toCloudNode()
	}

	fun toModel(resultRenamed: ResultRenamed<*>): CloudNodeModel<*> {
		return if (resultRenamed.value() is CloudFolder) {
			cloudFolderModelMapper.toModel(resultRenamed as ResultRenamed<CloudFolder>)
		} else {
			cloudFileModelMapper.toModel(resultRenamed as ResultRenamed<CloudFile>)
		}
	}

	override fun toModel(domainObject: CloudNode): CloudNodeModel<*> {
		return if (domainObject is CloudFolder) {
			cloudFolderModelMapper.toModel(domainObject)
		} else {
			cloudFileModelMapper.toModel(domainObject as CloudFile)
		}
	}
}
