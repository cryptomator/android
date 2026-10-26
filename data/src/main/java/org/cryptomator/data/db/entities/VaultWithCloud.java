package org.cryptomator.data.db.entities;

import androidx.room.Embedded;
import androidx.room.Relation;

/**
 * A vault together with the cloud it is stored in, which greenDAO used to resolve lazily through the
 * {@code FOLDER_CLOUD_ID} to-one relation.
 */
public class VaultWithCloud {

	@Embedded
	private VaultEntity vault;

	@Relation(parentColumn = "FOLDER_CLOUD_ID", entityColumn = "_id")
	private CloudEntity folderCloud;

	public VaultEntity getVault() {
		return vault;
	}

	public void setVault(VaultEntity vault) {
		this.vault = vault;
	}

	public CloudEntity getFolderCloud() {
		return folderCloud;
	}

	public void setFolderCloud(CloudEntity folderCloud) {
		this.folderCloud = folderCloud;
	}
}
