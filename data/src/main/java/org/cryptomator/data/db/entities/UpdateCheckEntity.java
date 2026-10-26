package org.cryptomator.data.db.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "UPDATE_CHECK_ENTITY")
public class UpdateCheckEntity {

	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "_id")
	private Long id;

	@ColumnInfo(name = "RELEASE_NOTE")
	private String releaseNote;

	@ColumnInfo(name = "VERSION")
	private String version;

	@ColumnInfo(name = "URL_TO_APK")
	private String urlToApk;

	@ColumnInfo(name = "APK_SHA256")
	private String apkSha256;

	@ColumnInfo(name = "URL_TO_RELEASE_NOTE")
	private String urlToReleaseNote;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getReleaseNote() {
		return releaseNote;
	}

	public void setReleaseNote(String releaseNote) {
		this.releaseNote = releaseNote;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public String getUrlToApk() {
		return urlToApk;
	}

	public void setUrlToApk(String urlToApk) {
		this.urlToApk = urlToApk;
	}

	public String getApkSha256() {
		return apkSha256;
	}

	public void setApkSha256(String apkSha256) {
		this.apkSha256 = apkSha256;
	}

	public String getUrlToReleaseNote() {
		return urlToReleaseNote;
	}

	public void setUrlToReleaseNote(String urlToReleaseNote) {
		this.urlToReleaseNote = urlToReleaseNote;
	}
}
