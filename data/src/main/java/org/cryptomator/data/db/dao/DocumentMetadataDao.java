package org.cryptomator.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import org.cryptomator.data.db.entities.DocumentMetadataEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.cryptomator.data.db.entities.DocumentMetadataEntity.STATUS_CREATING;
import static org.cryptomator.data.db.entities.DocumentMetadataEntity.STATUS_DELETING;
import static org.cryptomator.data.db.entities.DocumentMetadataEntity.STATUS_SYNCED;
import static org.cryptomator.data.db.entities.DocumentMetadataEntity.STATUS_UPLOADING;

/**
 * Subtrees are matched with {@code substr} instead of {@code LIKE} so that {@code %} and {@code _} in cleartext
 * names need no escaping.
 * <p>
 * Pending uploads hold the only copy of changes that have not reached the cloud, so a subtree that contains one is
 * kept when it is dropped because of the cloud or a failed change. Only deleting it explicitly removes it.
 */
@Dao
public interface DocumentMetadataDao {

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND PATH = :path")
	DocumentMetadataEntity load(long vaultId, String path);

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE _id = :id")
	DocumentMetadataEntity loadById(long id);

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND PARENT_PATH = :parentPath AND STATUS != '" + STATUS_DELETING + "'")
	List<DocumentMetadataEntity> loadChildren(long vaultId, String parentPath);

	@Query("SELECT EXISTS(SELECT 1 FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND PARENT_PATH = :parentPath AND STATUS != '" + STATUS_DELETING + "')")
	boolean hasChildren(long vaultId, String parentPath);

	@Query("SELECT _id FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND substr(PATH, 1, length(:path) + 1) = :path || '/'")
	List<Long> loadDescendantIds(long vaultId, String path);

	/**
	 * A pending upload alone doesn't count, since its local copy outlives the process and can be retried.
	 */
	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE STATUS IN ('" + STATUS_CREATING + "', '" + STATUS_DELETING + "') OR SOURCE_PATH IS NOT NULL")
	List<DocumentMetadataEntity> loadInterruptedChanges();

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND STATUS = '" + STATUS_UPLOADING + "'")
	List<DocumentMetadataEntity> loadPendingUploads(long vaultId);

	@Query("SELECT EXISTS(SELECT 1 FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND SOURCE_PATH = :path)")
	boolean isPendingMoveSource(long vaultId, String path);

	@Query("SELECT EXISTS(SELECT 1 FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND STATUS = '" + STATUS_UPLOADING + "' " //
			+ "AND (PATH = :path OR substr(PATH, 1, length(:path) + 1) = :path || '/'))")
	boolean hasPendingUploadInSubtree(long vaultId, String path);

	@Insert
	long insert(DocumentMetadataEntity entity);

	@Update
	void update(DocumentMetadataEntity entity);

	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET LAST_ENUMERATED_AT = :lastEnumeratedAt WHERE VAULT_ID = :vaultId AND PATH = :path")
	int updateLastEnumeratedAt(long vaultId, String path, Long lastEnumeratedAt);

	/**
	 * Records what was created in the cloud. A later change to the node, e.g. writing a file that was still being
	 * created, stays pending.
	 */
	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET SIZE = :size, LAST_MODIFIED = :lastModified, " //
			+ "STATUS = CASE WHEN STATUS = '" + STATUS_CREATING + "' THEN '" + STATUS_SYNCED + "' ELSE STATUS END " //
			+ "WHERE _id = :id")
	void markCreated(long id, Long size, Long lastModified);

	/**
	 * Records that the node has been moved to {@code cloudPath} in the cloud. If it has been moved again in the
	 * meantime, it stays pending, now moving away from {@code cloudPath}.
	 */
	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET SOURCE_PATH = CASE WHEN PATH = :cloudPath THEN NULL ELSE :cloudPath END WHERE _id = :id")
	void markMoved(long id, String cloudPath);

	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET STATUS = '" + STATUS_UPLOADING + "', UPLOAD_GENERATION = UPLOAD_GENERATION + 1 " //
			+ "WHERE VAULT_ID = :vaultId AND PATH = :path AND STATUS != '" + STATUS_DELETING + "'")
	void markUploading(long vaultId, String path);

	/**
	 * Records what was uploaded. The upload only ends if no later write of the file is waiting to be uploaded.
	 */
	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET SIZE = :size, LAST_MODIFIED = :lastModified, " //
			+ "STATUS = CASE WHEN STATUS = '" + STATUS_UPLOADING + "' AND UPLOAD_GENERATION = :uploadGeneration THEN '" + STATUS_SYNCED + "' ELSE STATUS END " //
			+ "WHERE _id = :id")
	void markUploaded(long id, Long size, Long lastModified, long uploadGeneration);

	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET STATUS = '" + STATUS_DELETING + "' WHERE VAULT_ID = :vaultId AND PATH = :path")
	void markDeleting(long vaultId, String path);

	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET STATUS = '" + STATUS_SYNCED + "' WHERE _id = :id")
	void markSynced(long id);

	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET SOURCE_PATH = NULL WHERE _id = :id")
	void clearSourcePath(long id);

	/**
	 * Moves the node at {@code sourcePath} and its descendants. The node keeps its pending change. Unless it is already
	 * moving, it is marked as moving away from {@code cloudPath}; a null {@code cloudPath} marks nothing.
	 */
	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET " //
			+ "PATH = :targetPath || substr(PATH, length(:sourcePath) + 1), " //
			+ "PARENT_PATH = CASE WHEN PATH = :sourcePath THEN :targetParentPath ELSE :targetPath || substr(PARENT_PATH, length(:sourcePath) + 1) END, " //
			+ "NAME = CASE WHEN PATH = :sourcePath THEN :targetName ELSE NAME END, " //
			+ "MIME_TYPE = CASE WHEN PATH = :sourcePath THEN :targetMimeType ELSE MIME_TYPE END, " //
			+ "SOURCE_PATH = CASE WHEN PATH = :sourcePath THEN coalesce(SOURCE_PATH, :cloudPath) ELSE SOURCE_PATH END " //
			+ "WHERE VAULT_ID = :vaultId AND (PATH = :sourcePath OR substr(PATH, 1, length(:sourcePath) + 1) = :sourcePath || '/')")
	void moveSubtree(long vaultId, String sourcePath, String targetPath, String targetParentPath, String targetName, String targetMimeType, String cloudPath);

	@Query("DELETE FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND (PATH = :path OR substr(PATH, 1, length(:path) + 1) = :path || '/')")
	void deleteSubtree(long vaultId, String path);

	@Query("DELETE FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND substr(PATH, 1, length(:path) + 1) = :path || '/'")
	void deleteDescendants(long vaultId, String path);

	/**
	 * Stores what the cloud reports for {@code entity} unless a pending change in the cache takes precedence.
	 */
	@Transaction
	default void store(DocumentMetadataEntity entity) {
		DocumentMetadataEntity existing = load(entity.getVaultId(), entity.getPath());
		if (existing == null) {
			if (!isPendingMoveSource(entity.getVaultId(), entity.getPath())) {
				insert(entity);
			}
			return;
		}
		if (existing.hasPendingChange()) {
			return;
		}
		if (existing.isFolder()) {
			if (entity.isFolder()) {
				entity.setLastEnumeratedAt(existing.getLastEnumeratedAt());
			} else if (hasPendingUploadInSubtree(entity.getVaultId(), entity.getPath())) {
				return;
			} else {
				deleteDescendants(entity.getVaultId(), entity.getPath());
			}
		}
		entity.setId(existing.getId());
		update(entity);
	}

	/**
	 * Replaces the cached children of {@code folder} with {@code children} and records {@code folder}'s
	 * {@code LAST_ENUMERATED_AT}. Child folders that are still present keep what is known about them, and children with
	 * pending changes are kept. Nothing is stored if {@code folder} has been moved or deleted in the meantime.
	 */
	@Transaction
	default void storeChildren(DocumentMetadataEntity folder, List<DocumentMetadataEntity> children) {
		long vaultId = folder.getVaultId();
		DocumentMetadataEntity existingFolder = load(vaultId, folder.getPath());
		boolean isRoot = folder.getParentPath() == null;
		if ((existingFolder == null && !isRoot) || (existingFolder != null && existingFolder.isDeleting())) {
			return;
		}
		Set<String> listedPaths = new HashSet<>();
		for (DocumentMetadataEntity child : children) {
			store(child);
			listedPaths.add(child.getPath());
		}
		for (DocumentMetadataEntity cached : loadChildren(vaultId, folder.getPath())) {
			if (!cached.hasPendingChange() && !listedPaths.contains(cached.getPath()) && !hasPendingUploadInSubtree(vaultId, cached.getPath())) {
				deleteSubtree(vaultId, cached.getPath());
			}
		}
		if (existingFolder == null) {
			insert(folder);
		} else {
			updateLastEnumeratedAt(vaultId, folder.getPath(), folder.getLastEnumeratedAt());
		}
	}

	@Transaction
	default void markDeletingWithDescendants(long vaultId, String path) {
		deleteDescendants(vaultId, path);
		markDeleting(vaultId, path);
	}

	@Transaction
	default void move(long vaultId, String sourcePath, String targetPath, String targetParentPath, String targetName, String targetMimeType) {
		deleteSubtree(vaultId, targetPath);
		moveSubtree(vaultId, sourcePath, targetPath, targetParentPath, targetName, targetMimeType, sourcePath);
	}

	/**
	 * Renames a node whose pending change could not be applied to the cloud because the name is taken there. The
	 * pending change itself is kept.
	 */
	@Transaction
	default void renamePending(long vaultId, String path, String targetPath, String targetName, String targetMimeType) {
		DocumentMetadataEntity pending = load(vaultId, path);
		moveSubtree(vaultId, path, targetPath, pending.getParentPath(), targetName, targetMimeType, null);
	}

	/**
	 * Undoes a move of the node with {@code id} that the cloud rejected. The node goes back to {@code SOURCE_PATH}, where
	 * it still is in the cloud, unless something has taken that place in the meantime. Both folders are marked for
	 * listing again.
	 */
	@Transaction
	default void revertMove(long id, String sourceMimeType) {
		DocumentMetadataEntity moved = loadById(id);
		if (moved == null || moved.getSourcePath() == null) {
			return;
		}
		long vaultId = moved.getVaultId();
		String sourcePath = moved.getSourcePath();
		if (load(vaultId, sourcePath) == null) {
			moveSubtree(vaultId, moved.getPath(), sourcePath, parentPathOf(sourcePath), sourcePath.substring(sourcePath.lastIndexOf('/') + 1), sourceMimeType, null);
		}
		clearSourcePath(id);
		updateLastEnumeratedAt(vaultId, moved.getParentPath(), null);
		updateLastEnumeratedAt(vaultId, parentPathOf(sourcePath), null);
	}

	/**
	 * Drops a change that could not be applied to the cloud and marks the folders it touched for listing again. That
	 * listing undoes a move, unless the moved subtree holds a pending upload, which stays where it was moved to. A node
	 * being created or deleted is dropped right away.
	 */
	@Transaction
	default void discardPendingChange(DocumentMetadataEntity pending) {
		long vaultId = pending.getVaultId();
		if (pending.getSourcePath() != null) {
			clearSourcePath(pending.getId());
			updateLastEnumeratedAt(vaultId, parentPathOf(pending.getSourcePath()), null);
		}
		if (pending.isCreating() || pending.isDeleting()) {
			if (hasPendingUploadInSubtree(vaultId, pending.getPath())) {
				markSynced(pending.getId());
			} else {
				deleteSubtree(vaultId, pending.getPath());
			}
		}
		updateLastEnumeratedAt(vaultId, pending.getParentPath(), null);
	}

	private static String parentPathOf(String path) {
		int lastSlash = path.lastIndexOf('/');
		return lastSlash < 0 ? "" : path.substring(0, lastSlash);
	}
}
