package org.cryptomator.data.db.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;

@Entity
public class UpdateCheckEntity extends DatabaseEntity {

	@Id
	private Long id;

	private String releaseNote;

	private String version;

	private String urlToApk;

	private String apkSha256;

	private String urlToReleaseNote;

	/**
	 * Constructs a new UpdateCheckEntity with all fields unset (null).
	 *
	 * <p>Required by the persistence framework for entity instantiation.</p>
	 */
	public UpdateCheckEntity() {
	}

	/**
	 * Creates a new UpdateCheckEntity with the given metadata.
	 *
	 * @param id                primary key (may be null before persistence)
	 * @param releaseNote       release note text or reference
	 * @param version           version string of the release
	 * @param urlToApk          download URL for the APK
	 * @param apkSha256         SHA-256 checksum of the APK
	 * @param urlToReleaseNote  URL referencing the release notes
	 */
	@Generated(hash = 867488251)
	public UpdateCheckEntity(Long id, String releaseNote, String version, String urlToApk, String apkSha256, String urlToReleaseNote) {
		this.id = id;
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

	/**
	 * Sets the entity's primary key.
	 *
	 * @param id the primary key value, or null if not yet assigned
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * Gets the version string of the update.
	 *
	 * @return the version string, or {@code null} if not set
	 */
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
