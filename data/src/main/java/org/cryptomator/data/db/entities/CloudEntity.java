package org.cryptomator.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "CLOUD_ENTITY")
public class CloudEntity {

	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "_id")
	private Long id;

	@NonNull
	@ColumnInfo(name = "TYPE")
	private String type;

	@ColumnInfo(name = "ACCESS_TOKEN")
	private String accessToken;

	@ColumnInfo(name = "ACCESS_TOKEN_CRYPTO_MODE")
	private String accessTokenCryptoMode;

	@ColumnInfo(name = "URL")
	private String url;

	@ColumnInfo(name = "USERNAME")
	private String username;

	@ColumnInfo(name = "WEBDAV_CERTIFICATE")
	private String webdavCertificate;

	@ColumnInfo(name = "S3_BUCKET")
	private String s3Bucket;

	@ColumnInfo(name = "S3_REGION")
	private String s3Region;

	@ColumnInfo(name = "S3_SECRET_KEY")
	private String s3SecretKey;

	@ColumnInfo(name = "S3_SECRET_KEY_CRYPTO_MODE")
	private String s3SecretKeyCryptoMode;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	@NonNull
	public String getType() {
		return type;
	}

	public void setType(@NonNull String type) {
		this.type = type;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getAccessTokenCryptoMode() {
		return accessTokenCryptoMode;
	}

	public void setAccessTokenCryptoMode(String accessTokenCryptoMode) {
		this.accessTokenCryptoMode = accessTokenCryptoMode;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getWebdavCertificate() {
		return webdavCertificate;
	}

	public void setWebdavCertificate(String webdavCertificate) {
		this.webdavCertificate = webdavCertificate;
	}

	public String getS3Bucket() {
		return s3Bucket;
	}

	public void setS3Bucket(String s3Bucket) {
		this.s3Bucket = s3Bucket;
	}

	public String getS3Region() {
		return s3Region;
	}

	public void setS3Region(String s3Region) {
		this.s3Region = s3Region;
	}

	public String getS3SecretKey() {
		return s3SecretKey;
	}

	public void setS3SecretKey(String s3SecretKey) {
		this.s3SecretKey = s3SecretKey;
	}

	public String getS3SecretKeyCryptoMode() {
		return s3SecretKeyCryptoMode;
	}

	public void setS3SecretKeyCryptoMode(String s3SecretKeyCryptoMode) {
		this.s3SecretKeyCryptoMode = s3SecretKeyCryptoMode;
	}
}
