package org.cryptomator.data.db.migrations.auto

import androidx.room.RenameColumn
import org.cryptomator.data.db.DatabaseAutoMigrationSpec

@RenameColumn.Entries(
	RenameColumn("CLOUD_ENTITY", "_id", "id"),
	RenameColumn("CLOUD_ENTITY", "TYPE", "type"),
	RenameColumn("CLOUD_ENTITY", "ACCESS_TOKEN", "accessToken"),
	RenameColumn("CLOUD_ENTITY", "ACCESS_TOKEN_CRYPTO_MODE", "accessTokenCryptoMode"),
	RenameColumn("CLOUD_ENTITY", "URL", "url"),
	RenameColumn("CLOUD_ENTITY", "USERNAME", "username"),
	RenameColumn("CLOUD_ENTITY", "WEBDAV_CERTIFICATE", "webdavCertificate"),
	RenameColumn("CLOUD_ENTITY", "S3_BUCKET", "s3Bucket"),
	RenameColumn("CLOUD_ENTITY", "S3_REGION", "s3Region"),
	RenameColumn("CLOUD_ENTITY", "S3_SECRET_KEY", "s3SecretKey"),
	RenameColumn("CLOUD_ENTITY", "S3_SECRET_KEY_CRYPTO_MODE", "s3SecretKeyCryptoMode"),
	//
	RenameColumn("UPDATE_CHECK_ENTITY", "_id", "id"),
	RenameColumn("UPDATE_CHECK_ENTITY", "LICENSE_TOKEN", "licenseToken"),
	RenameColumn("UPDATE_CHECK_ENTITY", "RELEASE_NOTE", "releaseNote"),
	RenameColumn("UPDATE_CHECK_ENTITY", "VERSION", "version"),
	RenameColumn("UPDATE_CHECK_ENTITY", "URL_TO_APK", "urlToApk"),
	RenameColumn("UPDATE_CHECK_ENTITY", "APK_SHA256", "apkSha256"),
	RenameColumn("UPDATE_CHECK_ENTITY", "URL_TO_RELEASE_NOTE", "urlToReleaseNote"),
	//
	RenameColumn("VAULT_ENTITY", "_id", "id"),
	RenameColumn("VAULT_ENTITY", "FOLDER_CLOUD_ID", "folderCloudId"),
	RenameColumn("VAULT_ENTITY", "FOLDER_PATH", "folderPath"),
	RenameColumn("VAULT_ENTITY", "FOLDER_NAME", "folderName"),
	RenameColumn("VAULT_ENTITY", "CLOUD_TYPE", "cloudType"),
	RenameColumn("VAULT_ENTITY", "PASSWORD", "password"),
	RenameColumn("VAULT_ENTITY", "PASSWORD_CRYPTO_MODE", "passwordCryptoMode"),
	RenameColumn("VAULT_ENTITY", "POSITION", "position"),
	RenameColumn("VAULT_ENTITY", "FORMAT", "format"),
	RenameColumn("VAULT_ENTITY", "SHORTENING_THRESHOLD", "shorteningThreshold"),
)
class AutoMigration14To15 : DatabaseAutoMigrationSpec()