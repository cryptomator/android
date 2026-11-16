package org.cryptomator.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import org.cryptomator.data.db.entities.UploadCheckpointEntity;

import java.util.List;

@Dao
public interface UploadCheckpointDao {

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	long insertOrReplace(UploadCheckpointEntity entity);

	@Query("SELECT * FROM UPLOAD_CHECKPOINT_ENTITY WHERE VAULT_ID = :vaultId")
	UploadCheckpointEntity findByVaultId(long vaultId);

	@Query("DELETE FROM UPLOAD_CHECKPOINT_ENTITY WHERE VAULT_ID = :vaultId")
	void deleteByVaultId(long vaultId);

	@Query("SELECT VAULT_ID FROM UPLOAD_CHECKPOINT_ENTITY")
	List<Long> findAllVaultIdsWithCheckpoints();

	@Query("UPDATE UPLOAD_CHECKPOINT_ENTITY SET COMPLETED_FILES = :completedFilesJson WHERE VAULT_ID = :vaultId")
	void updateCompletedFiles(long vaultId, String completedFilesJson);
}
