package org.cryptomator.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import org.cryptomator.data.db.entities.UploadCheckpointEntity;

import java.util.List;

@Dao
public interface UploadCheckpointDao {

	/**
	 * @return the row id, or -1 if the vault has a checkpoint already
	 */
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	long insertIfAbsent(UploadCheckpointEntity entity);

	@Query("SELECT * FROM UPLOAD_CHECKPOINT_ENTITY WHERE VAULT_ID = :vaultId")
	UploadCheckpointEntity findByVaultId(long vaultId);

	@Query("SELECT EXISTS(SELECT 1 FROM UPLOAD_CHECKPOINT_ENTITY WHERE VAULT_ID = :vaultId)")
	boolean existsForVault(long vaultId);

	@Query("SELECT SOURCE_FOLDER_URI FROM UPLOAD_CHECKPOINT_ENTITY WHERE VAULT_ID = :vaultId")
	String findSourceFolderUri(long vaultId);

	@Query("SELECT COUNT(*) FROM UPLOAD_CHECKPOINT_ENTITY WHERE SOURCE_FOLDER_URI = :sourceFolderUri")
	int countBySourceFolderUri(String sourceFolderUri);

	@Query("SELECT VAULT_ID FROM UPLOAD_CHECKPOINT_ENTITY")
	List<Long> findAllVaultIdsWithCheckpoints();

	/**
	 * Records a file only while its checkpoint exists, so that a checkpoint created later for the vault doesn't count it.
	 */
	@Query("INSERT OR IGNORE INTO UPLOAD_CHECKPOINT_FILE_ENTITY (VAULT_ID, PATH) " //
			+ "SELECT :vaultId, :path WHERE EXISTS(SELECT 1 FROM UPLOAD_CHECKPOINT_ENTITY WHERE VAULT_ID = :vaultId)")
	void insertCompletedFile(long vaultId, String path);

	@Query("SELECT PATH FROM UPLOAD_CHECKPOINT_FILE_ENTITY WHERE VAULT_ID = :vaultId")
	List<String> findCompletedFiles(long vaultId);

	@Query("SELECT COUNT(*) FROM UPLOAD_CHECKPOINT_FILE_ENTITY WHERE VAULT_ID = :vaultId")
	int countCompletedFiles(long vaultId);

	@Transaction
	default void deleteByVaultId(long vaultId) {
		deleteCheckpoint(vaultId);
		deleteCompletedFiles(vaultId);
	}

	@Query("DELETE FROM UPLOAD_CHECKPOINT_ENTITY WHERE VAULT_ID = :vaultId")
	void deleteCheckpoint(long vaultId);

	@Query("DELETE FROM UPLOAD_CHECKPOINT_FILE_ENTITY WHERE VAULT_ID = :vaultId")
	void deleteCompletedFiles(long vaultId);
}
