package org.cryptomator.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "CLOUD_ENTITY")
data class CloudEntity(
	@PrimaryKey @ColumnInfo("_id") override var id: Long?,
	@ColumnInfo("TYPE") var type: String,
	@ColumnInfo("ACCESS_TOKEN") var accessToken: String? = null,
	@ColumnInfo("ACCESS_TOKEN_CRYPTO_MODE") var accessTokenCryptoMode: String? = null,
	@ColumnInfo("URL") var url: String? = null,
	@ColumnInfo("USERNAME") var username: String? = null,
	@ColumnInfo("WEBDAV_CERTIFICATE") var webdavCertificate: String? = null,
	@ColumnInfo("S3_BUCKET") var s3Bucket: String? = null,
	@ColumnInfo("S3_REGION") var s3Region: String? = null,
	@ColumnInfo("S3_SECRET_KEY") var s3SecretKey: String? = null,
	@ColumnInfo("S3_SECRET_KEY_CRYPTO_MODE") var s3SecretKeyCryptoMode: String? = null,
) : DatabaseEntity {

}