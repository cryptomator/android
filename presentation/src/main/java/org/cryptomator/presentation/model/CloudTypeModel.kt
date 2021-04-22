package org.cryptomator.presentation.model

import org.cryptomator.domain.CloudType
import org.cryptomator.presentation.R

enum class CloudTypeModel(builder: Builder) {

	CRYPTO(Builder("CRYPTO", R.string.cloud_names_crypto)),  //
	DROPBOX(Builder("DROPBOX", R.string.cloud_names_dropbox) //
			.withCloudImageResource(R.drawable.dropbox) //
			.withVaultImageResource(R.drawable.dropbox_vault) //
			.withVaultSelectedImageResource(R.drawable.dropbox_vault_selected)),  //
	GOOGLE_DRIVE(Builder("GOOGLE_DRIVE", R.string.cloud_names_google_drive) //
			.withCloudImageResource(R.drawable.google_drive) //
			.withVaultImageResource(R.drawable.google_drive_vault) //
			.withVaultSelectedImageResource(R.drawable.google_drive_vault_selected)),  //
	ONEDRIVE(Builder("ONEDRIVE", R.string.cloud_names_onedrive) //
			.withCloudImageResource(R.drawable.onedrive) //
			.withVaultImageResource(R.drawable.onedrive_vault) //
			.withVaultSelectedImageResource(R.drawable.onedrive_vault_selected)),  //
	PCLOUD(Builder("PCLOUD", R.string.cloud_names_pcloud) //
			.withCloudImageResource(R.drawable.pcloud) //
			.withVaultImageResource(R.drawable.pcloud_vault) //
			.withVaultSelectedImageResource(R.drawable.pcloud_vault_selected) //
			.withMultiInstances()),  //
	WEBDAV(Builder("WEBDAV", R.string.cloud_names_webdav) //
			.withCloudImageResource(R.drawable.webdav) //
			.withVaultImageResource(R.drawable.webdav_vault) //
			.withVaultSelectedImageResource(R.drawable.webdav_vault_selected) //
			.withMultiInstances()),  //
	S3(Builder("S3", R.string.cloud_names_s3) //
			.withCloudImageResource(R.drawable.s3) //
			.withVaultImageResource(R.drawable.s3_vault) //
			.withVaultSelectedImageResource(R.drawable.s3_vault_selected) //
			.withMultiInstances()),  //
	LOCAL(Builder("LOCAL", R.string.cloud_names_local_storage) //
			.withCloudImageResource(R.drawable.local_fs) //
			.withVaultImageResource(R.drawable.local_fs_vault) //
			.withVaultSelectedImageResource(R.drawable.local_fs_vault_selected) //
			.withMultiInstances());

	val cloudName: String = builder.cloudName
	val displayNameResource: Int = builder.displayNameResource
	val cloudImageResource: Int = builder.cloudImageResource
	val vaultImageResource: Int = builder.vaultImageResource
	val vaultSelectedImageResource: Int = builder.vaultSelectedImageResource
	val isMultiInstance: Boolean = builder.multiInstances

	private class Builder(val cloudName: String, val displayNameResource: Int) {

		var cloudImageResource = 0
		var vaultImageResource = 0
		var vaultSelectedImageResource = 0
		var multiInstances = false

		fun withCloudImageResource(cloudImageLargeResource: Int): Builder {
			this.cloudImageResource = cloudImageLargeResource
			return this
		}

		fun withVaultImageResource(vaultImageResource: Int): Builder {
			this.vaultImageResource = vaultImageResource
			return this
		}

		fun withVaultSelectedImageResource(vaultSelectedImageResource: Int): Builder {
			this.vaultSelectedImageResource = vaultSelectedImageResource
			return this
		}

		fun withMultiInstances(): Builder {
			multiInstances = true
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
