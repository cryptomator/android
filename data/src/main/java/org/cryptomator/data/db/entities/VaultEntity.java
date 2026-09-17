package org.cryptomator.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import androidx.room.RoomWarnings;

/**
 * The index on {@code FOLDER_CLOUD_ID} that Room asks for would only speed up cascading deletes of a
 * handful of rows, so the index inherited from the greenDAO schema is kept as the only one.
 */
@SuppressWarnings(RoomWarnings.MISSING_INDEX_ON_FOREIGN_KEY_CHILD)
@Entity(tableName = "VAULT_ENTITY", //
		foreignKeys = @ForeignKey( //
				entity = CloudEntity.class, //
				parentColumns = "_id", //
				childColumns = "FOLDER_CLOUD_ID", //
				onDelete = ForeignKey.SET_NULL), //
		indices = @Index( //
				name = "IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID", //
				value = {"FOLDER_PATH", "FOLDER_CLOUD_ID"}, //
				unique = true))
public class VaultEntity {

	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "_id")
	private Long id;

	@ColumnInfo(name = "FOLDER_CLOUD_ID")
	private Long folderCloudId;

	@ColumnInfo(name = "FOLDER_PATH")
	private String folderPath;

	@ColumnInfo(name = "FOLDER_NAME")
	private String folderName;

	@NonNull
	@ColumnInfo(name = "CLOUD_TYPE")
	private String cloudType;

	@ColumnInfo(name = "PASSWORD")
	private String password;

	@ColumnInfo(name = "PASSWORD_CRYPTO_MODE")
	private String passwordCryptoMode;

	@ColumnInfo(name = "POSITION")
	private Integer position;

	@ColumnInfo(name = "FORMAT")
	private Integer format;

	@ColumnInfo(name = "SHORTENING_THRESHOLD")
	private Integer shorteningThreshold;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getFolderCloudId() {
		return folderCloudId;
	}

	public void setFolderCloudId(Long folderCloudId) {
		this.folderCloudId = folderCloudId;
	}

	public String getFolderPath() {
		return folderPath;
	}

	public void setFolderPath(String folderPath) {
		this.folderPath = folderPath;
	}

	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(String folderName) {
		this.folderName = folderName;
	}

	@NonNull
	public String getCloudType() {
		return cloudType;
	}

	public void setCloudType(@NonNull String cloudType) {
		this.cloudType = cloudType;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPasswordCryptoMode() {
		return passwordCryptoMode;
	}

	public void setPasswordCryptoMode(String passwordCryptoMode) {
		this.passwordCryptoMode = passwordCryptoMode;
	}

	public Integer getPosition() {
		return position;
	}

	public void setPosition(Integer position) {
		this.position = position;
	}

	public Integer getFormat() {
		return format;
	}

	public void setFormat(Integer format) {
		this.format = format;
	}

	public Integer getShorteningThreshold() {
		return shorteningThreshold;
	}

	public void setShorteningThreshold(Integer shorteningThreshold) {
		this.shorteningThreshold = shorteningThreshold;
	}
}
