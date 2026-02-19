package org.cryptomator.data.db.entities;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.annotation.Generated;

@Entity(indexes = {@Index(value = "vaultId", unique = true)})
public class UploadCheckpointEntity extends DatabaseEntity {

	@Id
	private Long id;

	@NotNull
	private Long vaultId;

	@NotNull
	private String type;

	@NotNull
	private String targetFolderPath;

	private String sourceFolderUri;

	private String sourceFolderName;

	private String pendingFileUris;

	@NotNull
	private String completedFiles;

	@NotNull
	private int totalFileCount;

	@NotNull
	private long timestamp;

	@Generated(hash = 482695414)
	public UploadCheckpointEntity(Long id, @NotNull Long vaultId,
			@NotNull String type, @NotNull String targetFolderPath,
			String sourceFolderUri, String sourceFolderName, String pendingFileUris,
			@NotNull String completedFiles, int totalFileCount, long timestamp) {
		this.id = id;
		this.vaultId = vaultId;
		this.type = type;
		this.targetFolderPath = targetFolderPath;
		this.sourceFolderUri = sourceFolderUri;
		this.sourceFolderName = sourceFolderName;
		this.pendingFileUris = pendingFileUris;
		this.completedFiles = completedFiles;
		this.totalFileCount = totalFileCount;
		this.timestamp = timestamp;
	}

	@Generated(hash = 1737881290)
	public UploadCheckpointEntity() {
	}

	@Override
	public Long getId() {
		return this.id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getVaultId() {
		return this.vaultId;
	}

	public void setVaultId(Long vaultId) {
		this.vaultId = vaultId;
	}

	public String getType() {
		return this.type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getTargetFolderPath() {
		return this.targetFolderPath;
	}

	public void setTargetFolderPath(String targetFolderPath) {
		this.targetFolderPath = targetFolderPath;
	}

	public String getSourceFolderUri() {
		return this.sourceFolderUri;
	}

	public void setSourceFolderUri(String sourceFolderUri) {
		this.sourceFolderUri = sourceFolderUri;
	}

	public String getSourceFolderName() {
		return this.sourceFolderName;
	}

	public void setSourceFolderName(String sourceFolderName) {
		this.sourceFolderName = sourceFolderName;
	}

	public String getPendingFileUris() {
		return this.pendingFileUris;
	}

	public void setPendingFileUris(String pendingFileUris) {
		this.pendingFileUris = pendingFileUris;
	}

	public String getCompletedFiles() {
		return this.completedFiles;
	}

	public void setCompletedFiles(String completedFiles) {
		this.completedFiles = completedFiles;
	}

	public int getTotalFileCount() {
		return this.totalFileCount;
	}

	public void setTotalFileCount(int totalFileCount) {
		this.totalFileCount = totalFileCount;
	}

	public long getTimestamp() {
		return this.timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
}
