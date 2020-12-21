package org.cryptomator.presentation.model.mappers

import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.usecases.ResultRenamed
import org.cryptomator.presentation.model.CloudFolderModel
import javax.inject.Inject

class CloudFolderModelMapper @Inject constructor() : ModelMapper<CloudFolderModel, CloudFolder>() {
	override fun fromModel(model: CloudFolderModel): CloudFolder {
		return model.toCloudNode()
	}

	override fun toModel(domainObject: CloudFolder): CloudFolderModel {
		return CloudFolderModel(domainObject)
	}

	fun toModel(resultRenamed: ResultRenamed<CloudFolder>): CloudFolderModel {
		return CloudFolderModel(resultRenamed)
	}
}
