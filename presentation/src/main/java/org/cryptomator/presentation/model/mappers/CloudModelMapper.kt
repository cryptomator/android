package org.cryptomator.presentation.model.mappers

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.model.*
import javax.inject.Inject

@PerView
class CloudModelMapper @Inject constructor() : ModelMapper<CloudModel, Cloud>() {
	override fun fromModel(model: CloudModel): Cloud {
		return model.toCloud()
	}

	override fun toModel(domainObject: Cloud): CloudModel {
		return when (CloudTypeModel.valueOf(domainObject.type())) {
			CloudTypeModel.DROPBOX -> DropboxCloudModel(domainObject)
			CloudTypeModel.GOOGLE_DRIVE -> GoogleDriveCloudModel(domainObject)
			CloudTypeModel.ONEDRIVE -> OnedriveCloudModel(domainObject)
			CloudTypeModel.CRYPTO -> CryptoCloudModel(domainObject)
			CloudTypeModel.LOCAL -> LocalStorageModel(domainObject)
			CloudTypeModel.WEBDAV -> WebDavCloudModel(domainObject)
		}
	}
}
