package org.cryptomator.presentation.model

import org.cryptomator.domain.CloudType
import org.cryptomator.presentation.R

enum class CloudTypeModel(builder: Builder) {

	CRYPTO(Builder("CRYPTO", R.string.cloud_names_crypto)),  //
	DROPBOX(Builder("DROPBOX", R.string.cloud_names_dropbox) //
			.withCloudImageResource(R.drawable.cloud_type_dropbox) //
			.withCloudImageLargeResource(R.drawable.cloud_type_dropbox_large)),  //
	GOOGLE_DRIVE(Builder("GOOGLE_DRIVE", R.string.cloud_names_google_drive) //
			.withCloudImageResource(R.drawable.cloud_type_google_drive) //
			.withCloudImageLargeResource(R.drawable.cloud_type_google_drive_large)),  //
	ONEDRIVE(Builder("ONEDRIVE", R.string.cloud_names_onedrive) //
			.withCloudImageResource(R.drawable.cloud_type_onedrive) //
			.withCloudImageLargeResource(R.drawable.cloud_type_onedrive_large)),  //
	LOCAL(Builder("LOCAL", R.string.cloud_names_local_storage) //
			.withCloudImageResource(R.drawable.storage_type_local) //
			.withCloudImageLargeResource(R.drawable.storage_type_local_large)), //
	WEBDAV(Builder("WEBDAV", R.string.cloud_names_webdav) //
			.withCloudImageResource(R.drawable.cloud_type_webdav) //
			.withCloudImageLargeResource(R.drawable.cloud_type_webdav_large) //
			.withMultiInstances()),  //
	WEB_DE(Builder("WEB", R.string.cloud_names_webde) //
			.withCloudImageResource(R.drawable.cloud_type_webdav) //
			.withCloudImageLargeResource(R.drawable.cloud_type_webde_large) //
			.withMultiInstances()
			.withPreFilledURL("https://webdav.smartdrive.web.de")),  //
	MAILBOX_ORG(Builder("MAILBOX", R.string.cloud_names_mailboxorg) //
			.withCloudImageResource(R.drawable.cloud_type_webdav) //
			.withCloudImageLargeResource(R.drawable.cloud_type_webdav_large) //
			.withMultiInstances()
			.withPreFilledURL("https://dav.mailbox.org/servlet/webdav.infostore"));

	val cloudName: String = builder.cloudName
	val displayNameResource: Int = builder.displayNameResource
	val cloudImageResource: Int = builder.cloudImageResource
	val cloudImageLargeResource: Int = builder.cloudImageLargeResource
	val isMultiInstance: Boolean = builder.multiInstances
	val preFilledURL: String = builder.preFilledURL

	private class Builder(val cloudName: String, val displayNameResource: Int) {
		var cloudImageResource = 0
		var cloudImageLargeResource = 0
		var multiInstances = false
		var preFilledURL = ""

		fun withCloudImageResource(cloudImageResource: Int): Builder {
			this.cloudImageResource = cloudImageResource
			return this
		}

		fun withCloudImageLargeResource(cloudImageLargeResource: Int): Builder {
			this.cloudImageLargeResource = cloudImageLargeResource
			return this
		}

		fun withMultiInstances(): Builder {
			multiInstances = true
			return this
		}

		fun withPreFilledURL(preFilledURL: String): Builder {
			this.preFilledURL = preFilledURL
			return this
		}
	}

	companion object {
		fun valueOf(type: CloudType): CloudTypeModel {
			return valueOf(type.name)
		}

		fun valueOf(type: CloudTypeModel): CloudType {
			return CloudType.valueOf(type.name)
		}
	}
}
