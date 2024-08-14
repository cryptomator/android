package org.cryptomator.data.db.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.NotNull;

@Entity
public class CloudEntity extends DatabaseEntity {

	@Id
	private Long id;

	@NotNull
	private String type;

	private String accessToken;

	private String accessTokenCryptoMode;

	private String url;

	private String username;

	private String webdavCertificate;

	private String s3Bucket;

	private String s3Region;

	private String s3SecretKey;

	private String s3SecretKeyCryptoMode;

	@Generated(hash = 930663276)
	public CloudEntity(Long id, @NotNull String type, String accessToken, String accessTokenCryptoMode, String url, String username, String webdavCertificate, String s3Bucket,
			String s3Region, String s3SecretKey, String s3SecretKeyCryptoMode) {
		this.id = id;
		this.type = type;
		this.accessToken = accessToken;
		this.accessTokenCryptoMode = accessTokenCryptoMode;
		this.url = url;
		this.username = username;
		this.webdavCertificate = webdavCertificate;
		this.s3Bucket = s3Bucket;
		this.s3Region = s3Region;
		this.s3SecretKey = s3SecretKey;
		this.s3SecretKeyCryptoMode = s3SecretKeyCryptoMode;
	}

	@Generated(hash = 1354152224)
	public CloudEntity() {
	}

	public String getAccessToken() {
		return this.accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
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
		return this.s3Bucket;
	}

	public void setS3Bucket(String s3Bucket) {
		this.s3Bucket = s3Bucket;
	}

	public String getS3Region() {
		return this.s3Region;
	}

	public void setS3Region(String s3Region) {
		this.s3Region = s3Region;
	}

	public String getS3SecretKey() {
		return this.s3SecretKey;
	}

	public void setS3SecretKey(String s3SecretKey) {
		this.s3SecretKey = s3SecretKey;
	}

	public String getAccessTokenCryptoMode() {
		return this.accessTokenCryptoMode;
	}

	public void setAccessTokenCryptoMode(String accessTokenCryptoMode) {
		this.accessTokenCryptoMode = accessTokenCryptoMode;
	}

	public String getS3SecretKeyCryptoMode() {
		return this.s3SecretKeyCryptoMode;
	}

	public void setS3SecretKeyCryptoMode(String s3SecretKeyCryptoMode) {
		this.s3SecretKeyCryptoMode = s3SecretKeyCryptoMode;
	}
}
