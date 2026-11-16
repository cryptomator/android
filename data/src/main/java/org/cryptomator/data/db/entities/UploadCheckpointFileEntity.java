package org.cryptomator.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

/**
 * A file of the folder upload checkpointed in {@link UploadCheckpointEntity} that has been uploaded, by its path relative
 * to the upload's target folder.
 */
@Entity(tableName = "UPLOAD_CHECKPOINT_FILE_ENTITY", //
		primaryKeys = {"VAULT_ID", "PATH"})
public class UploadCheckpointFileEntity {

	@ColumnInfo(name = "VAULT_ID")
	private long vaultId;

	@NonNull
	@ColumnInfo(name = "PATH")
	private String path = "";

	public long getVaultId() {
		return vaultId;
	}

	public void setVaultId(long vaultId) {
		this.vaultId = vaultId;
	}

	@NonNull
	public String getPath() {
		return path;
	}

	public void setPath(@NonNull String path) {
		this.path = path;
	}
}
