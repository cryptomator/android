package org.cryptomator.data.db.entities;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

/**
 * Decrypted local copy of a file, stored under {@code LOCAL_NAME}. {@code LAST_MODIFIED} is the cloud modification date
 * of the version the copy is based on, which tells whether the copy is still current. The row follows its
 * {@link DocumentMetadataEntity} when that is moved and is dropped with it. The local file is not deleted with the row,
 * so files without a row have to be cleaned up separately.
 */
@Entity(tableName = "CACHED_FILE_ENTITY", //
		primaryKeys = {"VAULT_ID", "PATH"}, //
		foreignKeys = @ForeignKey( //
				entity = DocumentMetadataEntity.class, //
				parentColumns = {"VAULT_ID", "PATH"}, //
				childColumns = {"VAULT_ID", "PATH"}, //
				onDelete = ForeignKey.CASCADE, //
				onUpdate = ForeignKey.CASCADE))
public class CachedFileEntity {

	@ColumnInfo(name = "VAULT_ID")
	private long vaultId;

	@NonNull
	@ColumnInfo(name = "PATH")
	private String path = "";

	@NonNull
	@ColumnInfo(name = "LOCAL_NAME")
	private String localName = "";

	@ColumnInfo(name = "LAST_MODIFIED")
	private Long lastModified;

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

	@NonNull
	public String getLocalName() {
		return localName;
	}

	public void setLocalName(@NonNull String localName) {
		this.localName = localName;
	}

	public Long getLastModified() {
		return lastModified;
	}

	public void setLastModified(Long lastModified) {
		this.lastModified = lastModified;
	}
}
