package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider
import org.cryptomator.presentation.R

enum class CloudTypeModel(builder: Builder) {

	CRYPTO(Builder("CRYPTO", R.string.cloud_names_crypto)),  //
	DROPBOX(
		Builder("DROPBOX", R.string.cloud_names_dropbox) //
			.withCloudImageResource(R.drawable.dropbox) //
			.withVaultImageResource(R.drawable.dropbox_vault) //
			.withVaultSelectedImageResource(R.drawable.dropbox_vault_selected)
	),  //
	GOOGLE_DRIVE(
		Builder("GOOGLE_DRIVE", R.string.cloud_names_google_drive) //
			.withCloudImageResource(R.drawable.google_drive) //
			.withVaultImageResource(R.drawable.google_drive_vault) //
			.withVaultSelectedImageResource(R.drawable.google_drive_vault_selected)
	),  //
	ONEDRIVE(
		Builder("ONEDRIVE", R.string.cloud_names_onedrive) //
			.withCloudImageResource(R.drawable.onedrive) //
			.withVaultImageResource(R.drawable.onedrive_vault) //
			.withVaultSelectedImageResource(R.drawable.onedrive_vault_selected)
			.withMultiInstances()
	),  //
	SHAREPOINT(
		Builder("SHAREPOINT", R.string.cloud_names_sharepoint) //
			.withCloudImageResource(R.drawable.sharepoint) //
			.withVaultImageResource(R.drawable.sharepoint_vault) //
			.withVaultSelectedImageResource(R.drawable.sharepoint_vault_selected) //
			.withMultiInstances()
	),  //
	PCLOUD(
		Builder("PCLOUD", R.string.cloud_names_pcloud) //
			.withCloudImageResource(R.drawable.pcloud) //
			.withVaultImageResource(R.drawable.pcloud_vault) //
			.withVaultSelectedImageResource(R.drawable.pcloud_vault_selected) //
			.withMultiInstances()
	),  //
	BOX(
		Builder("BOX", R.string.cloud_names_box) //
			.withCloudImageResource(R.drawable.box) //
			.withVaultImageResource(R.drawable.box_vault) //
			.withVaultSelectedImageResource(R.drawable.box_vault_selected) //
			.withMultiInstances()
	),  //
	WEBDAV(
		Builder("WEBDAV", R.string.cloud_names_webdav) //
			.withCloudImageResource(R.drawable.webdav) //
			.withVaultImageResource(R.drawable.webdav_vault) //
			.withVaultSelectedImageResource(R.drawable.webdav_vault_selected) //
			.withMultiInstances()
	),  //
	S3(
		Builder("S3", R.string.cloud_names_s3) //
			.withCloudImageResource(R.drawable.s3) //
			.withVaultImageResource(R.drawable.s3_vault) //
			.withVaultSelectedImageResource(R.drawable.s3_vault_selected) //
			.withMultiInstances()
	),  //
	LOCAL(
		Builder("LOCAL", R.string.cloud_names_local_storage) //
			.withCloudImageResource(R.drawable.local_fs) //
			.withVaultImageResource(R.drawable.local_fs_vault) //
			.withVaultSelectedImageResource(R.drawable.local_fs_vault_selected) //
			.withMultiInstances()
	),  //
	ONE_AND_ONE_DSL(WebDavProvider.ONE_AND_ONE_DSL, R.string.cloud_names_one_and_one_dsl, R.drawable.webdav_one_and_one, R.drawable.webdav_one_and_one_vault, R.drawable.webdav_one_and_one_vault_selected),  //
	ONE_AND_ONE_WEBHOSTING(WebDavProvider.ONE_AND_ONE_WEBHOSTING, R.string.cloud_names_one_and_one_webhosting, R.drawable.webdav_one_and_one, R.drawable.webdav_one_and_one_vault, R.drawable.webdav_one_and_one_vault_selected),  //
	DISROOT(WebDavProvider.DISROOT, R.string.cloud_names_disroot, R.drawable.webdav_disroot, R.drawable.webdav_disroot_vault, R.drawable.webdav_disroot_vault_selected, R.string.screen_webdav_settings_disroot_hint),  //
	FREENETCLOUD(WebDavProvider.FREENETCLOUD, R.string.cloud_names_freenetcloud, R.drawable.webdav_freenetcloud, R.drawable.webdav_freenetcloud_vault, R.drawable.webdav_freenetcloud_vault_selected),  //
	GMX(WebDavProvider.GMX, R.string.cloud_names_gmx, R.drawable.webdav_gmx, R.drawable.webdav_gmx_vault, R.drawable.webdav_gmx_vault_selected),  //
	HIDRIVE_IONOS(WebDavProvider.HIDRIVE_IONOS, R.string.cloud_names_hidrive_ionos, R.drawable.webdav_hidrive_ionos, R.drawable.webdav_hidrive_ionos_vault, R.drawable.webdav_hidrive_ionos_vault_selected, R.string.screen_webdav_settings_hidrive_ionos_hint),  //
	HIDRIVE_STRATO(WebDavProvider.HIDRIVE_STRATO, R.string.cloud_names_hidrive_strato, R.drawable.webdav_hidrive_strato, R.drawable.webdav_hidrive_strato_vault, R.drawable.webdav_hidrive_strato_vault_selected, R.string.screen_webdav_settings_hidrive_strato_hint),  //
	ICEDRIVE(WebDavProvider.ICEDRIVE, R.string.cloud_names_icedrive, R.drawable.webdav_icedrive, R.drawable.webdav_icedrive_vault, R.drawable.webdav_icedrive_vault_selected, R.string.screen_webdav_settings_icedrive_hint),  //
	KDRIVE(WebDavProvider.KDRIVE, R.string.cloud_names_kdrive, R.drawable.webdav_kdrive, R.drawable.webdav_kdrive_vault, R.drawable.webdav_kdrive_vault_selected, R.string.screen_webdav_settings_kdrive_hint),  //
	KOOFR(WebDavProvider.KOOFR, R.string.cloud_names_koofr, R.drawable.webdav_koofr, R.drawable.webdav_koofr_vault, R.drawable.webdav_koofr_vault_selected, R.string.screen_webdav_settings_koofr_hint),  //
	MAGENTACLOUD(WebDavProvider.MAGENTACLOUD, R.string.cloud_names_magentacloud, R.drawable.webdav_magentacloud, R.drawable.webdav_magentacloud_vault, R.drawable.webdav_magentacloud_vault_selected, R.string.screen_webdav_settings_magentacloud_hint),  //
	MAILBOX_ORG(WebDavProvider.MAILBOX_ORG, R.string.cloud_names_mailbox_org, R.drawable.webdav_mailbox_org, R.drawable.webdav_mailbox_org_vault, R.drawable.webdav_mailbox_org_vault_selected),  //
	MAIL_RU(WebDavProvider.MAIL_RU, R.string.cloud_names_mail_ru, R.drawable.webdav_mail_ru, R.drawable.webdav_mail_ru_vault, R.drawable.webdav_mail_ru_vault_selected),  //
	NEXTCLOUD(WebDavProvider.NEXTCLOUD, R.string.cloud_names_nextcloud, R.drawable.webdav_nextcloud, R.drawable.webdav_nextcloud_vault, R.drawable.webdav_nextcloud_vault_selected, R.string.screen_webdav_settings_app_password_with_2fa_hint),  //
	OPENCLOUD(WebDavProvider.OPENCLOUD, R.string.cloud_names_opencloud, R.drawable.webdav_opencloud, R.drawable.webdav_opencloud_vault, R.drawable.webdav_opencloud_vault_selected, R.string.screen_webdav_settings_opencloud_hint),  //
	OWNCLOUD(WebDavProvider.OWNCLOUD, R.string.cloud_names_owncloud, R.drawable.webdav_owncloud, R.drawable.webdav_owncloud_vault, R.drawable.webdav_owncloud_vault_selected, R.string.screen_webdav_settings_app_password_with_2fa_hint),  //
	PCLOUD_EU(WebDavProvider.PCLOUD_EU, R.string.cloud_names_pcloud_eu, R.drawable.pcloud, R.drawable.pcloud_vault, R.drawable.pcloud_vault_selected, R.string.screen_webdav_settings_pcloud_hint),  //
	PCLOUD_US(WebDavProvider.PCLOUD_US, R.string.cloud_names_pcloud_us, R.drawable.pcloud, R.drawable.pcloud_vault, R.drawable.pcloud_vault_selected, R.string.screen_webdav_settings_pcloud_hint),  //
	SEAFILE(WebDavProvider.SEAFILE, R.string.cloud_names_seafile, R.drawable.webdav_seafile, R.drawable.webdav_seafile_vault, R.drawable.webdav_seafile_vault_selected),  //
	STACK(WebDavProvider.STACK, R.string.cloud_names_stack, R.drawable.webdav_stack, R.drawable.webdav_stack_vault, R.drawable.webdav_stack_vault_selected),  //
	SWITCHDRIVE(WebDavProvider.SWITCHDRIVE, R.string.cloud_names_switchdrive, R.drawable.webdav_switchdrive, R.drawable.webdav_switchdrive_vault, R.drawable.webdav_switchdrive_vault_selected),  //
	SYNCWERK(WebDavProvider.SYNCWERK, R.string.cloud_names_syncwerk, R.drawable.webdav_syncwerk, R.drawable.webdav_syncwerk_vault, R.drawable.webdav_syncwerk_vault_selected),  //
	WEB_DE(WebDavProvider.WEB_DE, R.string.cloud_names_web_de, R.drawable.webdav_web_de, R.drawable.webdav_web_de_vault, R.drawable.webdav_web_de_vault_selected),  //
	WOELKLI(WebDavProvider.WOELKLI, R.string.cloud_names_woelkli, R.drawable.webdav_woelkli, R.drawable.webdav_woelkli_vault, R.drawable.webdav_woelkli_vault_selected),  //
	YANDEX_DISK(WebDavProvider.YANDEX_DISK, R.string.cloud_names_yandex_disk, R.drawable.webdav_yandex_disk, R.drawable.webdav_yandex_disk_vault, R.drawable.webdav_yandex_disk_vault_selected, R.string.screen_webdav_settings_app_password_with_2fa_hint);

	constructor(webDavProvider: WebDavProvider, displayNameResource: Int, cloudImageResource: Int, vaultImageResource: Int, vaultSelectedImageResource: Int, webDavHintResource: Int = 0) : this(
		Builder(webDavProvider.name, displayNameResource) //
			.withCloudImageResource(cloudImageResource) //
			.withVaultImageResource(vaultImageResource) //
			.withVaultSelectedImageResource(vaultSelectedImageResource) //
			.withMultiInstances() //
			.withWebDavProvider(webDavProvider, webDavHintResource)
	)

	val cloudName: String = builder.cloudName
	val displayNameResource: Int = builder.displayNameResource
	val cloudImageResource: Int = builder.cloudImageResource
	val vaultImageResource: Int = builder.vaultImageResource
	val vaultSelectedImageResource: Int = builder.vaultSelectedImageResource
	val isMultiInstance: Boolean = builder.multiInstances
	val webDavProvider: WebDavProvider? = builder.webDavProvider
	val webDavHintResource: Int = builder.webDavHintResource

	private class Builder(val cloudName: String, val displayNameResource: Int) {

		var cloudImageResource = 0
		var vaultImageResource = 0
		var vaultSelectedImageResource = 0
		var multiInstances = false
		var webDavProvider: WebDavProvider? = null
		var webDavHintResource = 0

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

		fun withWebDavProvider(webDavProvider: WebDavProvider, webDavHintResource: Int): Builder {
			this.webDavProvider = webDavProvider
			this.webDavHintResource = webDavHintResource
			return this
		}
	}

	companion object {

		fun valueOf(type: CloudType): CloudTypeModel {
			return valueOf(type.name)
		}

		fun valueOf(type: CloudTypeModel): CloudType {
			return if (type.webDavProvider != null) CloudType.WEBDAV else CloudType.valueOf(type.name)
		}

		fun valueOf(webDavProvider: WebDavProvider): CloudTypeModel {
			return entries.first { it.webDavProvider == webDavProvider }
		}

		fun valueOf(cloud: Cloud): CloudTypeModel {
			return (cloud as? WebDavCloud)?.provider()?.let { valueOf(it) } ?: valueOf(cloud.type()!!)
		}
	}
}
