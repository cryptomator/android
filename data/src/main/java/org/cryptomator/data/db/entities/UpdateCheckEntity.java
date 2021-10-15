package org.cryptomator.data.db.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;

@Entity
public class UpdateCheckEntity extends DatabaseEntity {

	@Id
	private Long id;

	private String licenseToken;

	private String releaseNote;

	private String version;

	private String urlToApk;

	private String apkSha256;

	private String urlToReleaseNote;

	public UpdateCheckEntity() {
	}

	@Generated(hash = 67239496)
	public UpdateCheckEntity(Long id, String licenseToken, String releaseNote, String version, String urlToApk, String apkSha256, String urlToReleaseNote) {
		this.id = id;
		this.licenseToken = licenseToken;
		this.releaseNote = releaseNote;
		this.version = version;
		this.urlToApk = urlToApk;
		this.apkSha256 = apkSha256;
		this.urlToReleaseNote = urlToReleaseNote;
	}

	@Override
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getLicenseToken() {
		return this.licenseToken;
	}

	public void setLicenseToken(String licenseToken) {
		this.licenseToken = licenseToken;
	}

	public String getVersion() {
		return this.version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getUrlToApk() {
		return this.urlToApk;
	}

	public void setUrlToApk(String urlToApk) {
		this.urlToApk = urlToApk;
	}

	public String getReleaseNote() {
		return this.releaseNote;
	}

	public void setReleaseNote(String releaseNote) {
		this.releaseNote = releaseNote;
	}

	public String getUrlToReleaseNote() {
		return this.urlToReleaseNote;
	}

	public void setUrlToReleaseNote(String urlToReleaseNote) {
		this.urlToReleaseNote = urlToReleaseNote;
	}

	public String getApkSha256() {
		return this.apkSha256;
	}

	public void setApkSha256(String apkSha256) {
		this.apkSha256 = apkSha256;
	}
}
