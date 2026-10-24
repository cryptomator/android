package org.cryptomator.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import org.cryptomator.data.db.entities.CachedFileEntity;
import org.cryptomator.data.db.entities.DocumentMetadataEntity;

import java.util.List;

@Dao
public interface CachedFileDao {

	@Query("SELECT * FROM CACHED_FILE_ENTITY WHERE VAULT_ID = :vaultId AND PATH = :path")
	CachedFileEntity load(long vaultId, String path);

	@Query("SELECT * FROM CACHED_FILE_ENTITY WHERE LOCAL_NAME = :localName")
	CachedFileEntity loadByLocalName(String localName);

	@Query("SELECT LOCAL_NAME FROM CACHED_FILE_ENTITY")
	List<String> loadLocalNames();

	@Query("SELECT * FROM CACHED_FILE_ENTITY WHERE LOCAL_MODIFIED_WHEN_OPENED IS NOT NULL")
	List<CachedFileEntity> loadOpenedForWriting();

	/**
	 * The copies that can be downloaded again, i.e. all but those of files with a pending upload. Copies of files hidden
	 * by a pending deletion are kept as well, so that they are still there if the deletion fails.
	 */
	@Query("SELECT CACHED_FILE_ENTITY.* FROM CACHED_FILE_ENTITY JOIN DOCUMENT_METADATA_ENTITY " //
			+ "ON CACHED_FILE_ENTITY.VAULT_ID = DOCUMENT_METADATA_ENTITY.VAULT_ID AND CACHED_FILE_ENTITY.PATH = DOCUMENT_METADATA_ENTITY.PATH " //
			+ "WHERE DOCUMENT_METADATA_ENTITY.STATUS != '" + DocumentMetadataEntity.STATUS_UPLOADING + "' AND " + DocumentMetadataDao.IS_VISIBLE)
	List<CachedFileEntity> loadEvictable();

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	void store(CachedFileEntity entity);

	@Query("UPDATE CACHED_FILE_ENTITY SET LAST_MODIFIED = :lastModified WHERE VAULT_ID = :vaultId AND PATH = :path")
	void updateLastModified(long vaultId, String path, Long lastModified);

	/**
	 * Records {@code localModified} for the copy {@code localName} unless it is recorded as open for writing already,
	 * which keeps the earlier date, see {@link CachedFileEntity}.
	 */
	@Query("UPDATE CACHED_FILE_ENTITY SET LOCAL_MODIFIED_WHEN_OPENED = coalesce(LOCAL_MODIFIED_WHEN_OPENED, :localModified) WHERE LOCAL_NAME = :localName")
	void markOpenedForWriting(String localName, long localModified);

	@Query("UPDATE CACHED_FILE_ENTITY SET LOCAL_MODIFIED_WHEN_OPENED = NULL WHERE LOCAL_NAME = :localName")
	void markClosedForWriting(String localName);

	@Query("DELETE FROM CACHED_FILE_ENTITY WHERE VAULT_ID = :vaultId AND PATH = :path")
	void delete(long vaultId, String path);
}
