package org.cryptomator.presentation.model.mappers

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CloudTypeModel
import org.cryptomator.presentation.model.CryptoCloudModel
import org.cryptomator.presentation.model.DropboxCloudModel
import org.cryptomator.presentation.model.GoogleDriveCloudModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.OnedriveCloudModel
import org.cryptomator.presentation.model.PCloudModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
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
			CloudTypeModel.LOCAL -> LocalStorageModel(domainObject)
			CloudTypeModel.ONEDRIVE -> OnedriveCloudModel(domainObject)
			CloudTypeModel.PCLOUD -> PCloudModel(domainObject)
			CloudTypeModel.S3 -> S3CloudModel(domainObject)
			CloudTypeModel.CRYPTO -> CryptoCloudModel(domainObject)
			CloudTypeModel.WEBDAV -> WebDavCloudModel(domainObject)
		}
	}
}
