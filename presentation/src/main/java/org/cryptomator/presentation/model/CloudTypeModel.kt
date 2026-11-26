package org.cryptomator.presentation.model

import android.content.Context
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.S3Provider
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
			.withMultiInstances(R.string.screen_cloud_settings_onedrive_connections)
	),  //
	SHAREPOINT(
		Builder("SHAREPOINT", R.string.cloud_names_sharepoint) //
			.withCloudImageResource(R.drawable.sharepoint) //
			.withVaultImageResource(R.drawable.sharepoint_vault) //
			.withVaultSelectedImageResource(R.drawable.sharepoint_vault_selected) //
			.withMultiInstances(R.string.screen_cloud_settings_sharepoint_connections)
	),  //
	PCLOUD(
		Builder("PCLOUD", R.string.cloud_names_pcloud) //
			.withCloudImageResource(R.drawable.pcloud) //
			.withVaultImageResource(R.drawable.pcloud_vault) //
			.withVaultSelectedImageResource(R.drawable.pcloud_vault_selected) //
			.withMultiInstances(R.string.screen_cloud_settings_pcloud_connections)
	),  //
	BOX(
		Builder("BOX", R.string.cloud_names_box) //
			.withCloudImageResource(R.drawable.box) //
			.withVaultImageResource(R.drawable.box_vault) //
			.withVaultSelectedImageResource(R.drawable.box_vault_selected) //
			.withMultiInstances(R.string.screen_cloud_settings_box_connections)
	),  //
	WEBDAV(
		Builder("WEBDAV", R.string.cloud_names_webdav) //
			.withCloudImageResource(R.drawable.webdav) //
			.withVaultImageResource(R.drawable.webdav_vault) //
			.withVaultSelectedImageResource(R.drawable.webdav_vault_selected) //
			.withMultiInstances(R.string.screen_cloud_settings_webdav_connections)
	),  //
	S3(
		Builder("S3", R.string.cloud_names_s3) //
			.withCloudImageResource(R.drawable.s3) //
			.withVaultImageResource(R.drawable.s3_vault) //
			.withVaultSelectedImageResource(R.drawable.s3_vault_selected) //
			.withMultiInstances(R.string.screen_cloud_settings_s3_connections)
	),  //
	LOCAL(
		Builder("LOCAL", R.string.cloud_names_local_storage) //
			.withCloudImageResource(R.drawable.local_fs) //
			.withVaultImageResource(R.drawable.local_fs_vault) //
			.withVaultSelectedImageResource(R.drawable.local_fs_vault_selected) //
			.withMultiInstances(R.string.screen_cloud_settings_local_storage_locations)
	),  //
	ONE_AND_ONE(WebDavProvider.ONE_AND_ONE, R.string.cloud_names_one_and_one, R.drawable.webdav_one_and_one, R.drawable.webdav_one_and_one_vault, R.drawable.webdav_one_and_one_vault_selected),  //
	AKAMAI(S3Provider.AKAMAI, R.string.cloud_names_akamai, R.drawable.s3_akamai, R.drawable.s3_akamai_vault, R.drawable.s3_akamai_vault_selected, R.string.screen_s3_settings_akamai_hint),  //
	AMAZON_S3(S3Provider.AMAZON_S3, R.string.cloud_names_amazon_s3, R.drawable.s3_amazon_s3, R.drawable.s3_amazon_s3_vault, R.drawable.s3_amazon_s3_vault_selected),  //
	BACKBLAZE_B2(S3Provider.BACKBLAZE_B2, R.string.cloud_names_backblaze_b2, R.drawable.s3_backblaze_b2, R.drawable.s3_backblaze_b2_vault, R.drawable.s3_backblaze_b2_vault_selected, R.string.screen_s3_settings_backblaze_b2_hint),  //
	CLOUDFLARE_R2(S3Provider.CLOUDFLARE_R2, R.string.cloud_names_cloudflare_r2, R.drawable.s3_cloudflare_r2, R.drawable.s3_cloudflare_r2_vault, R.drawable.s3_cloudflare_r2_vault_selected, R.string.screen_s3_settings_cloudflare_r2_hint),  //
	DIGITALOCEAN_SPACES(S3Provider.DIGITALOCEAN_SPACES, R.string.cloud_names_digitalocean_spaces, R.drawable.s3_digitalocean_spaces, R.drawable.s3_digitalocean_spaces_vault, R.drawable.s3_digitalocean_spaces_vault_selected),  //
	DISROOT(WebDavProvider.DISROOT, R.string.cloud_names_disroot, R.drawable.webdav_disroot, R.drawable.webdav_disroot_vault, R.drawable.webdav_disroot_vault_selected, R.string.screen_webdav_settings_disroot_hint),  //
	EXOSCALE(S3Provider.EXOSCALE, R.string.cloud_names_exoscale, R.drawable.s3_exoscale, R.drawable.s3_exoscale_vault, R.drawable.s3_exoscale_vault_selected),  //
	FREENETCLOUD(WebDavProvider.FREENETCLOUD, R.string.cloud_names_freenetcloud, R.drawable.webdav_freenetcloud, R.drawable.webdav_freenetcloud_vault, R.drawable.webdav_freenetcloud_vault_selected),  //
	GMX(WebDavProvider.GMX, R.string.cloud_names_gmx, R.drawable.webdav_gmx, R.drawable.webdav_gmx_vault, R.drawable.webdav_gmx_vault_selected),  //
	GOOGLE_CLOUD_STORAGE(S3Provider.GOOGLE_CLOUD_STORAGE, R.string.cloud_names_google_cloud_storage, R.drawable.s3_google_cloud_storage, R.drawable.s3_google_cloud_storage_vault, R.drawable.s3_google_cloud_storage_vault_selected, R.string.screen_s3_settings_google_cloud_storage_hint),  //
	HETZNER(S3Provider.HETZNER, R.string.cloud_names_hetzner, R.drawable.s3_hetzner, R.drawable.s3_hetzner_vault, R.drawable.s3_hetzner_vault_selected),  //
	HIDRIVE_IONOS(WebDavProvider.HIDRIVE_IONOS, R.string.cloud_names_hidrive_ionos, R.drawable.webdav_hidrive_ionos, R.drawable.webdav_hidrive_ionos_vault, R.drawable.webdav_hidrive_ionos_vault_selected, R.string.screen_webdav_settings_hidrive_ionos_hint),  //
	HIDRIVE_STRATO(WebDavProvider.HIDRIVE_STRATO, R.string.cloud_names_hidrive_strato, R.drawable.webdav_hidrive_strato, R.drawable.webdav_hidrive_strato_vault, R.drawable.webdav_hidrive_strato_vault_selected, R.string.screen_webdav_settings_hidrive_strato_hint),  //
	ICEDRIVE(WebDavProvider.ICEDRIVE, R.string.cloud_names_icedrive, R.drawable.webdav_icedrive, R.drawable.webdav_icedrive_vault, R.drawable.webdav_icedrive_vault_selected, R.string.screen_webdav_settings_icedrive_hint),  //
	IONOS(S3Provider.IONOS, R.string.cloud_names_ionos, R.drawable.s3_ionos, R.drawable.s3_ionos_vault, R.drawable.s3_ionos_vault_selected),  //
	KDRIVE(WebDavProvider.KDRIVE, R.string.cloud_names_kdrive, R.drawable.webdav_kdrive, R.drawable.webdav_kdrive_vault, R.drawable.webdav_kdrive_vault_selected, R.string.screen_webdav_settings_kdrive_hint),  //
	KOOFR(WebDavProvider.KOOFR, R.string.cloud_names_koofr, R.drawable.webdav_koofr, R.drawable.webdav_koofr_vault, R.drawable.webdav_koofr_vault_selected, R.string.screen_webdav_settings_koofr_hint),  //
	MAGENTACLOUD(WebDavProvider.MAGENTACLOUD, R.string.cloud_names_magentacloud, R.drawable.webdav_magentacloud, R.drawable.webdav_magentacloud_vault, R.drawable.webdav_magentacloud_vault_selected, R.string.screen_webdav_settings_magentacloud_hint),  //
	MAILBOX_ORG(WebDavProvider.MAILBOX_ORG, R.string.cloud_names_mailbox_org, R.drawable.webdav_mailbox_org, R.drawable.webdav_mailbox_org_vault, R.drawable.webdav_mailbox_org_vault_selected),  //
	MAIL_RU(WebDavProvider.MAIL_RU, R.string.cloud_names_mail_ru, R.drawable.webdav_mail_ru, R.drawable.webdav_mail_ru_vault, R.drawable.webdav_mail_ru_vault_selected),  //
	MINIO(S3Provider.MINIO, R.string.cloud_names_minio, R.drawable.s3_minio, R.drawable.s3_minio_vault, R.drawable.s3_minio_vault_selected),  //
	NEXTCLOUD(WebDavProvider.NEXTCLOUD, R.string.cloud_names_nextcloud, R.drawable.webdav_nextcloud, R.drawable.webdav_nextcloud_vault, R.drawable.webdav_nextcloud_vault_selected, R.string.screen_webdav_settings_app_password_with_2fa_hint),  //
	OPENCLOUD(WebDavProvider.OPENCLOUD, R.string.cloud_names_opencloud, R.drawable.webdav_opencloud, R.drawable.webdav_opencloud_vault, R.drawable.webdav_opencloud_vault_selected, R.string.screen_webdav_settings_opencloud_hint),  //
	OVHCLOUD(S3Provider.OVHCLOUD, R.string.cloud_names_ovhcloud, R.drawable.s3_ovhcloud, R.drawable.s3_ovhcloud_vault, R.drawable.s3_ovhcloud_vault_selected),  //
	OWNCLOUD(WebDavProvider.OWNCLOUD, R.string.cloud_names_owncloud, R.drawable.webdav_owncloud, R.drawable.webdav_owncloud_vault, R.drawable.webdav_owncloud_vault_selected, R.string.screen_webdav_settings_app_password_with_2fa_hint),  //
	SCALEWAY(S3Provider.SCALEWAY, R.string.cloud_names_scaleway, R.drawable.s3_scaleway, R.drawable.s3_scaleway_vault, R.drawable.s3_scaleway_vault_selected),  //
	SEAFILE(WebDavProvider.SEAFILE, R.string.cloud_names_seafile, R.drawable.webdav_seafile, R.drawable.webdav_seafile_vault, R.drawable.webdav_seafile_vault_selected),  //
	STACK(WebDavProvider.STACK, R.string.cloud_names_stack, R.drawable.webdav_stack, R.drawable.webdav_stack_vault, R.drawable.webdav_stack_vault_selected),  //
	STORJ(S3Provider.STORJ, R.string.cloud_names_storj, R.drawable.s3_storj, R.drawable.s3_storj_vault, R.drawable.s3_storj_vault_selected, R.string.screen_s3_settings_storj_hint),  //
	SWITCHDRIVE(WebDavProvider.SWITCHDRIVE, R.string.cloud_names_switchdrive, R.drawable.webdav_switchdrive, R.drawable.webdav_switchdrive_vault, R.drawable.webdav_switchdrive_vault_selected),  //
	SYNCWERK(WebDavProvider.SYNCWERK, R.string.cloud_names_syncwerk, R.drawable.webdav_syncwerk, R.drawable.webdav_syncwerk_vault, R.drawable.webdav_syncwerk_vault_selected),  //
	VULTR(S3Provider.VULTR, R.string.cloud_names_vultr, R.drawable.s3_vultr, R.drawable.s3_vultr_vault, R.drawable.s3_vultr_vault_selected, R.string.screen_s3_settings_vultr_hint),  //
	WASABI(S3Provider.WASABI, R.string.cloud_names_wasabi, R.drawable.s3_wasabi, R.drawable.s3_wasabi_vault, R.drawable.s3_wasabi_vault_selected),  //
	WEB_DE(WebDavProvider.WEB_DE, R.string.cloud_names_web_de, R.drawable.webdav_web_de, R.drawable.webdav_web_de_vault, R.drawable.webdav_web_de_vault_selected),  //
	WOELKLI(WebDavProvider.WOELKLI, R.string.cloud_names_woelkli, R.drawable.webdav_woelkli, R.drawable.webdav_woelkli_vault, R.drawable.webdav_woelkli_vault_selected),  //
	YANDEX_DISK(WebDavProvider.YANDEX_DISK, R.string.cloud_names_yandex_disk, R.drawable.webdav_yandex_disk, R.drawable.webdav_yandex_disk_vault, R.drawable.webdav_yandex_disk_vault_selected, R.string.screen_webdav_settings_app_password_with_2fa_hint);

	constructor(webDavProvider: WebDavProvider, displayNameResource: Int, cloudImageResource: Int, vaultImageResource: Int, vaultSelectedImageResource: Int, providerHintResource: Int = 0) : this(
		Builder(webDavProvider.name, displayNameResource, cloudImageResource, vaultImageResource, vaultSelectedImageResource, providerHintResource) //
			.withWebDavProvider(webDavProvider)
	)

	constructor(s3Provider: S3Provider, displayNameResource: Int, cloudImageResource: Int, vaultImageResource: Int, vaultSelectedImageResource: Int, providerHintResource: Int = 0) : this(
		Builder(s3Provider.name, displayNameResource, cloudImageResource, vaultImageResource, vaultSelectedImageResource, providerHintResource) //
			.withS3Provider(s3Provider)
	)

	val cloudName: String = builder.cloudName
	val displayNameResource: Int = builder.displayNameResource
	val cloudImageResource: Int = builder.cloudImageResource
	val vaultImageResource: Int = builder.vaultImageResource
	val vaultSelectedImageResource: Int = builder.vaultSelectedImageResource
	val isMultiInstance: Boolean = builder.multiInstances
	private val connectionsTitleResource: Int = builder.connectionsTitleResource
	val webDavProvider: WebDavProvider? = builder.webDavProvider
	val s3Provider: S3Provider? = builder.s3Provider
	val providerHintResource: Int = builder.providerHintResource

	val isProvider: Boolean
		get() = webDavProvider != null || s3Provider != null

	fun connectionsTitle(context: Context): String? {
		return when {
			!isMultiInstance -> null
			isProvider -> context.getString(connectionsTitleResource, context.getString(displayNameResource))
			else -> context.getString(connectionsTitleResource)
		}
	}

	private class Builder(val cloudName: String, val displayNameResource: Int) {

		var cloudImageResource = 0
		var vaultImageResource = 0
		var vaultSelectedImageResource = 0
		var multiInstances = false
		var connectionsTitleResource = 0
		var webDavProvider: WebDavProvider? = null
		var s3Provider: S3Provider? = null
		var providerHintResource = 0

		constructor(cloudName: String, displayNameResource: Int, cloudImageResource: Int, vaultImageResource: Int, vaultSelectedImageResource: Int, providerHintResource: Int) : this(cloudName, displayNameResource) {
			this.cloudImageResource = cloudImageResource
			this.vaultImageResource = vaultImageResource
			this.vaultSelectedImageResource = vaultSelectedImageResource
			this.providerHintResource = providerHintResource
			withMultiInstances(R.string.screen_cloud_settings_provider_connections)
		}

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

		fun withMultiInstances(connectionsTitleResource: Int): Builder {
			multiInstances = true
			this.connectionsTitleResource = connectionsTitleResource
			return this
		}

		fun withWebDavProvider(webDavProvider: WebDavProvider): Builder {
			this.webDavProvider = webDavProvider
			return this
		}

		fun withS3Provider(s3Provider: S3Provider): Builder {
			this.s3Provider = s3Provider
			return this
		}
	}

	companion object {

		fun valueOf(type: CloudType): CloudTypeModel {
			return valueOf(type.name)
		}

		fun valueOf(type: CloudTypeModel): CloudType {
			return when {
				type.webDavProvider != null -> CloudType.WEBDAV
				type.s3Provider != null -> CloudType.S3
				else -> CloudType.valueOf(type.name)
			}
		}

		fun valueOf(webDavProvider: WebDavProvider): CloudTypeModel {
			return entries.first { it.webDavProvider == webDavProvider }
		}

		fun valueOf(s3Provider: S3Provider): CloudTypeModel {
			return entries.first { it.s3Provider == s3Provider }
		}

		fun valueOf(cloud: Cloud): CloudTypeModel {
			return (cloud as? WebDavCloud)?.provider()?.let { valueOf(it) } //
				?: (cloud as? S3Cloud)?.provider()?.let { valueOf(it) } //
				?: valueOf(cloud.type()!!)
		}
	}
}
