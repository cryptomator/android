package org.cryptomator.data.db.entities;

import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Cached metadata of a node inside a vault. Rows are kept while the vault is locked. {@code PATH} is the cleartext path
 * relative to the vault root, without leading or trailing slashes; the root itself is stored with an empty path and no
 * parent path.
 * <p>
 * A {@code STATUS} other than {@link #STATUS_SYNCED} marks a change that has been applied to the cache but not yet
 * to the cloud: a node being created, a file whose local copy has been written and still has to be uploaded, or a node
 * being deleted, which is hidden until the deletion is done.
 * <p>
 * Independently of that, a {@code SOURCE_PATH} marks a node that has been moved but is still at {@code SOURCE_PATH} in
 * the cloud.
 * <p>
 * Each write of a file increments {@code UPLOAD_GENERATION}, so that only the upload of the latest write ends
 * {@link #STATUS_UPLOADING}.
 */
@Entity(tableName = "DOCUMENT_METADATA_ENTITY", //
		foreignKeys = @ForeignKey( //
				entity = VaultEntity.class, //
				parentColumns = "_id", //
				childColumns = "VAULT_ID", //
				onDelete = ForeignKey.CASCADE), //
		indices = { //
				@Index( //
						name = "IDX_DOCUMENT_METADATA_ENTITY_VAULT_ID_PATH", //
						value = {"VAULT_ID", "PATH"}, //
						unique = true), //
				@Index( //
						name = "IDX_DOCUMENT_METADATA_ENTITY_VAULT_ID_PARENT_PATH", //
						value = {"VAULT_ID", "PARENT_PATH"})})
public class DocumentMetadataEntity {

	public static final String STATUS_SYNCED = "SYNCED";
	public static final String STATUS_CREATING = "CREATING";
	public static final String STATUS_UPLOADING = "UPLOADING";
	public static final String STATUS_DELETING = "DELETING";

	/**
	 * Identifies the node in its documentId. Unlike {@code PATH} it is kept when the node is renamed or moved.
	 */
	@PrimaryKey(autoGenerate = true)
	@ColumnInfo(name = "_id")
	private Long id;

	@ColumnInfo(name = "VAULT_ID")
	private long vaultId;

	@NonNull
	@ColumnInfo(name = "PATH")
	private String path = "";

	@ColumnInfo(name = "PARENT_PATH")
	private String parentPath;

	@NonNull
	@ColumnInfo(name = "NAME")
	private String name = "";

	@NonNull
	@ColumnInfo(name = "MIME_TYPE")
	private String mimeType = "";

	@ColumnInfo(name = "SIZE")
	private Long size;

	@ColumnInfo(name = "LAST_MODIFIED")
	private Long lastModified;

	@ColumnInfo(name = "LAST_ENUMERATED_AT")
	private Long lastEnumeratedAt;

	@NonNull
	@ColumnInfo(name = "STATUS")
	private String status = STATUS_SYNCED;

	@ColumnInfo(name = "SOURCE_PATH")
	private String sourcePath;

	@ColumnInfo(name = "UPLOAD_GENERATION")
	private long uploadGeneration;

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
	public String getPath() {
		return path;
	}

	public void setPath(@NonNull String path) {
		this.path = path;
	}

	public String getParentPath() {
		return parentPath;
	}

	public void setParentPath(String parentPath) {
		this.parentPath = parentPath;
	}

	@NonNull
	public String getName() {
		return name;
	}

	public void setName(@NonNull String name) {
		this.name = name;
	}

	@NonNull
	public String getMimeType() {
		return mimeType;
	}

	public void setMimeType(@NonNull String mimeType) {
		this.mimeType = mimeType;
	}

	public boolean isFolder() {
		return DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType);
	}

	public Long getSize() {
		return size;
	}

	public void setSize(Long size) {
		this.size = size;
	}

	public Long getLastModified() {
		return lastModified;
	}

	public void setLastModified(Long lastModified) {
		this.lastModified = lastModified;
	}

	public Long getLastEnumeratedAt() {
		return lastEnumeratedAt;
	}

	public void setLastEnumeratedAt(Long lastEnumeratedAt) {
		this.lastEnumeratedAt = lastEnumeratedAt;
	}

	@NonNull
	public String getStatus() {
		return status;
	}

	public void setStatus(@NonNull String status) {
		this.status = status;
	}

	public boolean isCreating() {
		return STATUS_CREATING.equals(status);
	}

	public boolean isUploading() {
		return STATUS_UPLOADING.equals(status);
	}

	public boolean isDeleting() {
		return STATUS_DELETING.equals(status);
	}

	public String getSourcePath() {
		return sourcePath;
	}

	public void setSourcePath(String sourcePath) {
		this.sourcePath = sourcePath;
	}

	public boolean hasPendingChange() {
		return !STATUS_SYNCED.equals(status) || sourcePath != null;
	}

	public long getUploadGeneration() {
		return uploadGeneration;
	}

	public void setUploadGeneration(long uploadGeneration) {
		this.uploadGeneration = uploadGeneration;
	}
}
