package org.cryptomator.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "UPLOAD_CHECKPOINT_ENTITY", //
		indices = @Index( //
				name = "IDX_UPLOAD_CHECKPOINT_ENTITY_VAULT_ID", //
				value = "VAULT_ID", //
				unique = true))
public class UploadCheckpointEntity {

	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "_id")
	private Long id;

	@ColumnInfo(name = "VAULT_ID")
	private long vaultId;

	@NonNull
	@ColumnInfo(name = "TYPE")
	private String type = "";

	@NonNull
	@ColumnInfo(name = "TARGET_FOLDER_PATH")
	private String targetFolderPath = "";

	@ColumnInfo(name = "SOURCE_FOLDER_URI")
	private String sourceFolderUri;

	@ColumnInfo(name = "SOURCE_FOLDER_NAME")
	private String sourceFolderName;

	@ColumnInfo(name = "PENDING_FILE_URIS")
	private String pendingFileUris;

	@NonNull
	@ColumnInfo(name = "COMPLETED_FILES")
	private String completedFiles = "";

	@ColumnInfo(name = "TOTAL_FILE_COUNT")
	private int totalFileCount;

	@ColumnInfo(name = "TIMESTAMP")
	private long timestamp;

	@ColumnInfo(name = "REPLACING")
	private boolean replacing;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public long getVaultId() {
		return vaultId;
	}

	public void setVaultId(long vaultId) {
		this.vaultId = vaultId;
	}

	@NonNull
	public String getType() {
		return type;
	}

	public void setType(@NonNull String type) {
		this.type = type;
	}

	@NonNull
	public String getTargetFolderPath() {
		return targetFolderPath;
	}

	public void setTargetFolderPath(@NonNull String targetFolderPath) {
		this.targetFolderPath = targetFolderPath;
	}

	public String getSourceFolderUri() {
		return sourceFolderUri;
	}

	public void setSourceFolderUri(String sourceFolderUri) {
		this.sourceFolderUri = sourceFolderUri;
	}

	public String getSourceFolderName() {
		return sourceFolderName;
	}

	public void setSourceFolderName(String sourceFolderName) {
		this.sourceFolderName = sourceFolderName;
	}

	public String getPendingFileUris() {
		return pendingFileUris;
	}

	public void setPendingFileUris(String pendingFileUris) {
		this.pendingFileUris = pendingFileUris;
	}

	@NonNull
	public String getCompletedFiles() {
		return completedFiles;
	}

	public void setCompletedFiles(@NonNull String completedFiles) {
		this.completedFiles = completedFiles;
	}

	public int getTotalFileCount() {
		return totalFileCount;
	}

	public void setTotalFileCount(int totalFileCount) {
		this.totalFileCount = totalFileCount;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}

	public boolean isReplacing() {
		return replacing;
	}

	public void setReplacing(boolean replacing) {
		this.replacing = replacing;
	}
}
