package org.cryptomator.presentation.model.mappers

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.di.PerView
import org.cryptomator.presentation.model.BoxCloudModel
import org.cryptomator.presentation.model.CloudModel
import org.cryptomator.presentation.model.CryptoCloudModel
import org.cryptomator.presentation.model.DropboxCloudModel
import org.cryptomator.presentation.model.GoogleDriveCloudModel
import org.cryptomator.presentation.model.LocalStorageModel
import org.cryptomator.presentation.model.OnedriveCloudModel
import org.cryptomator.presentation.model.PCloudModel
import org.cryptomator.presentation.model.S3CloudModel
import org.cryptomator.presentation.model.SharepointCloudModel
import org.cryptomator.presentation.model.WebDavCloudModel
import javax.inject.Inject

@PerView
class CloudModelMapper @Inject constructor() : ModelMapper<CloudModel, Cloud>() {

	override fun fromModel(model: CloudModel): Cloud {
		return model.toCloud()
	}

	override fun toModel(domainObject: Cloud): CloudModel {
		return when (domainObject.type()) {
			CloudType.DROPBOX -> DropboxCloudModel(domainObject)
			CloudType.GOOGLE_DRIVE -> GoogleDriveCloudModel(domainObject)
			CloudType.LOCAL -> LocalStorageModel(domainObject)
			CloudType.ONEDRIVE -> OnedriveCloudModel(domainObject)
			CloudType.SHAREPOINT -> SharepointCloudModel(domainObject)
			CloudType.PCLOUD -> PCloudModel(domainObject)
			CloudType.BOX -> BoxCloudModel(domainObject)
			CloudType.S3 -> S3CloudModel(domainObject)
			CloudType.CRYPTO -> CryptoCloudModel(domainObject)
			CloudType.WEBDAV -> WebDavCloudModel(domainObject)
			null -> throw IllegalStateException("The type of the object shouldn't be null")
		}
	}
}
