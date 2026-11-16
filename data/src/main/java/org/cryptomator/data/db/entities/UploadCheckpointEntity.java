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
	@ColumnInfo(name = "TARGET_FOLDER_PATH")
	private String targetFolderPath = "";

	@NonNull
	@ColumnInfo(name = "SOURCE_FOLDER_URI")
	private String sourceFolderUri = "";

	@NonNull
	@ColumnInfo(name = "SOURCE_FOLDER_NAME")
	private String sourceFolderName = "";

	@ColumnInfo(name = "TOTAL_FILE_COUNT")
	private int totalFileCount;

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
	public String getTargetFolderPath() {
		return targetFolderPath;
	}

	public void setTargetFolderPath(@NonNull String targetFolderPath) {
		this.targetFolderPath = targetFolderPath;
	}

	@NonNull
	public String getSourceFolderUri() {
		return sourceFolderUri;
	}

	public void setSourceFolderUri(@NonNull String sourceFolderUri) {
		this.sourceFolderUri = sourceFolderUri;
	}

	@NonNull
	public String getSourceFolderName() {
		return sourceFolderName;
	}

	public void setSourceFolderName(@NonNull String sourceFolderName) {
		this.sourceFolderName = sourceFolderName;
	}

	public int getTotalFileCount() {
		return totalFileCount;
	}

	public void setTotalFileCount(int totalFileCount) {
		this.totalFileCount = totalFileCount;
	}

	public boolean isReplacing() {
		return replacing;
	}

	public void setReplacing(boolean replacing) {
		this.replacing = replacing;
	}
}
