package org.cryptomator.presentation.model

import android.os.Build
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
	WEBDAV(Builder("WEBDAV", R.string.cloud_names_webdav) //
			.withCloudImageResource(R.drawable.cloud_type_webdav) //
			.withCloudImageLargeResource(R.drawable.cloud_type_webdav_large) //
			.withMultiInstances()),  //
	LOCAL(Builder("LOCAL", R.string.cloud_names_local_storage) //
			.withCloudImageResource(R.drawable.storage_type_local) //
			.withCloudImageLargeResource(R.drawable.storage_type_local_large) //
			.withMultiInstancesIfLollipopOrLater());

	val cloudName: String
	val displayNameResource: Int
	val cloudImageResource: Int
	val cloudImageLargeResource: Int
	val isMultiInstance: Boolean

	private class Builder(val cloudName: String, val displayNameResource: Int) {
		var cloudImageResource = 0
		var cloudImageLargeResource = 0
		var multiInstances = false

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

		fun withMultiInstancesIfLollipopOrLater(): Builder {
			multiInstances = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
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

	init {
		cloudName = builder.cloudName
		displayNameResource = builder.displayNameResource
		cloudImageResource = builder.cloudImageResource
		cloudImageLargeResource = builder.cloudImageLargeResource
		isMultiInstance = builder.multiInstances
	}
}
