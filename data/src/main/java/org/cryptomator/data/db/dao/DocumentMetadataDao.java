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
import static org.cryptomator.data.db.entities.DocumentMetadataEntity.STATUS_SYNCED;
import static org.cryptomator.data.db.entities.DocumentMetadataEntity.STATUS_UPLOADING;

/**
 * Subtrees are matched with {@code substr} instead of {@code LIKE} so that {@code %} and {@code _} in cleartext
 * names need no escaping.
 * <p>
 * Pending uploads hold the only copy of changes that have not reached the cloud, so a subtree that contains one is
 * kept when it is dropped because of the cloud or a failed change. Only deleting it explicitly removes it. A subtree
 * being deleted is kept as well, so that it can come back if the cloud rejects the deletion.
 */
@Dao
public interface DocumentMetadataDao {

	/**
	 * Matches the nodes that are not hidden by a pending deletion of themselves or of an ancestor.
	 */
	String IS_VISIBLE = "NOT EXISTS(SELECT 1 FROM DOCUMENT_METADATA_ENTITY AS DELETED " //
			+ "WHERE DELETED.VAULT_ID = DOCUMENT_METADATA_ENTITY.VAULT_ID AND DELETED.PENDING_DELETE IS NOT NULL " //
			+ "AND (DELETED.PATH = DOCUMENT_METADATA_ENTITY.PATH OR substr(DOCUMENT_METADATA_ENTITY.PATH, 1, length(DELETED.PATH) + 1) = DELETED.PATH || '/'))";

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND PATH = :path")
	DocumentMetadataEntity load(long vaultId, String path);

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE _id = :id")
	DocumentMetadataEntity loadById(long id);

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND PATH = :path AND " + IS_VISIBLE)
	DocumentMetadataEntity loadVisible(long vaultId, String path);

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE _id = :id AND " + IS_VISIBLE)
	DocumentMetadataEntity loadVisibleById(long id);

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND PARENT_PATH = :parentPath AND " + IS_VISIBLE)
	List<DocumentMetadataEntity> loadChildren(long vaultId, String parentPath);

	@Query("SELECT EXISTS(SELECT 1 FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND PARENT_PATH = :parentPath AND " + IS_VISIBLE + ")")
	boolean hasChildren(long vaultId, String parentPath);

	@Query("SELECT _id FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND substr(PATH, 1, length(:path) + 1) = :path || '/'")
	List<Long> loadDescendantIds(long vaultId, String path);

	/**
	 * A pending upload alone doesn't count, since its local copy outlives the process and can be retried.
	 */
	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE STATUS = '" + STATUS_CREATING + "' OR SOURCE_PATH IS NOT NULL")
	List<DocumentMetadataEntity> loadInterruptedChanges();

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE PENDING_DELETE IS NOT NULL")
	List<DocumentMetadataEntity> loadPendingDeletes();

	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND STATUS = '" + STATUS_UPLOADING + "' AND " + IS_VISIBLE)
	List<DocumentMetadataEntity> loadPendingUploads(long vaultId);

	/**
	 * The pending uploads whose local copy has never been recorded, e.g. because the process ended while it was written.
	 */
	@Query("SELECT * FROM DOCUMENT_METADATA_ENTITY WHERE STATUS = '" + STATUS_UPLOADING + "' AND NOT EXISTS(SELECT 1 FROM CACHED_FILE_ENTITY " //
			+ "WHERE CACHED_FILE_ENTITY.VAULT_ID = DOCUMENT_METADATA_ENTITY.VAULT_ID AND CACHED_FILE_ENTITY.PATH = DOCUMENT_METADATA_ENTITY.PATH)")
	List<DocumentMetadataEntity> loadUploadsWithoutLocalCopy();

	/**
	 * Whether a node that is being moved can still be at {@code path} in the cloud. That is the case if its
	 * {@code SOURCE_PATH} is {@code path}, or if the node is in the folder {@code parentPath} of the cache and its
	 * {@code SOURCE_PATH} ends with {@code name}, since a folder above it may have been moved meanwhile.
	 */
	@Query("SELECT EXISTS(SELECT 1 FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND SOURCE_PATH IS NOT NULL " //
			+ "AND (SOURCE_PATH = :path OR (PARENT_PATH = :parentPath AND substr('/' || SOURCE_PATH, -length(:name) - 1) = '/' || :name)))")
	boolean isPendingMoveSource(long vaultId, String path, String parentPath, String name);

	@Query("SELECT EXISTS(SELECT 1 FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND STATUS = '" + STATUS_UPLOADING + "' " //
			+ "AND (PATH = :path OR substr(PATH, 1, length(:path) + 1) = :path || '/'))")
	boolean hasPendingUploadInSubtree(long vaultId, String path);

	@Query("SELECT EXISTS(SELECT 1 FROM DOCUMENT_METADATA_ENTITY WHERE VAULT_ID = :vaultId AND PENDING_DELETE IS NOT NULL " //
			+ "AND (PATH = :path OR substr(PATH, 1, length(:path) + 1) = :path || '/'))")
	boolean hasPendingDeleteInSubtree(long vaultId, String path);

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
	 * Records {@code cloudPath} as where the node's pending moves start in the cloud.
	 */
	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET SOURCE_PATH = :cloudPath WHERE _id = :id")
	void setSourcePath(long id, String cloudPath);

	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET STATUS = '" + STATUS_UPLOADING + "', UPLOAD_GENERATION = UPLOAD_GENERATION + 1 " //
			+ "WHERE VAULT_ID = :vaultId AND PATH = :path")
	void markUploading(long vaultId, String path);

	/**
	 * Records what was uploaded. The upload only ends if no later write of the file is waiting to be uploaded.
	 */
	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET SIZE = :size, LAST_MODIFIED = :lastModified, " //
			+ "STATUS = CASE WHEN STATUS = '" + STATUS_UPLOADING + "' AND UPLOAD_GENERATION = :uploadGeneration THEN '" + STATUS_SYNCED + "' ELSE STATUS END " //
			+ "WHERE _id = :id")
	void markUploaded(long id, Long size, Long lastModified, long uploadGeneration);

	/**
	 * Hides the node at {@code path} and its descendants until the deletion with {@code token} is done or undone.
	 */
	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET PENDING_DELETE = :token WHERE VAULT_ID = :vaultId AND PATH = :path")
	void markPendingDelete(long vaultId, String path, long token);

	/**
	 * Brings back the node with {@code id} after its deletion with {@code token} failed, unless it has been deleted
	 * again since.
	 */
	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET PENDING_DELETE = NULL WHERE _id = :id AND PENDING_DELETE = :token")
	void clearPendingDelete(long id, long token);

	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET STATUS = '" + STATUS_SYNCED + "' WHERE _id = :id")
	void markSynced(long id);

	@Query("UPDATE DOCUMENT_METADATA_ENTITY SET SOURCE_PATH = NULL WHERE _id = :id")
	void clearSourcePath(long id);

	/**
	 * Moves the node at {@code sourcePath} and its descendants. They keep their pending changes. Unless the node is
	 * already moving, it is marked as moving away from {@code cloudPath}; a null {@code cloudPath} marks nothing.
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
			if (!isPendingMoveSource(entity.getVaultId(), entity.getPath(), entity.getParentPath(), entity.getName())) {
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
			} else if (hasPendingUploadInSubtree(entity.getVaultId(), entity.getPath()) || hasPendingDeleteInSubtree(entity.getVaultId(), entity.getPath())) {
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
		if ((existingFolder == null && !isRoot) || (existingFolder != null && loadVisible(vaultId, folder.getPath()) == null)) {
			return;
		}
		Set<String> listedPaths = new HashSet<>();
		for (DocumentMetadataEntity child : children) {
			store(child);
			listedPaths.add(child.getPath());
		}
		for (DocumentMetadataEntity cached : loadChildren(vaultId, folder.getPath())) {
			if (!cached.hasPendingChange() && !listedPaths.contains(cached.getPath()) && !hasPendingUploadInSubtree(vaultId, cached.getPath()) && !hasPendingDeleteInSubtree(vaultId, cached.getPath())) {
				deleteSubtree(vaultId, cached.getPath());
			}
		}
		if (existingFolder == null) {
			insert(folder);
		} else {
			updateLastEnumeratedAt(vaultId, folder.getPath(), folder.getLastEnumeratedAt());
		}
	}

	/**
	 * Clears the deletions that were pending when the process ended, so that the nodes reappear. Whether the cloud
	 * deleted them is told by the next listing of their folders.
	 */
	@Transaction
	default void recoverPendingDeletes() {
		for (DocumentMetadataEntity deleting : loadPendingDeletes()) {
			clearPendingDelete(deleting.getId(), deleting.getPendingDelete());
			updateLastEnumeratedAt(deleting.getVaultId(), deleting.getParentPath(), null);
		}
	}

	@Transaction
	default void move(long vaultId, String sourcePath, String targetPath, String targetParentPath, String targetName, String targetMimeType) {
		deleteSubtree(vaultId, targetPath);
		moveSubtree(vaultId, sourcePath, targetPath, targetParentPath, targetName, targetMimeType, sourcePath);
	}

	/**
	 * Renames a node with a pending change in the cache only, e.g. because the name is taken in the cloud. The pending
	 * change itself is kept. A non-null {@code cloudPath} marks the node as moving away from there unless it is already
	 * moving, see {@link #moveSubtree}.
	 */
	@Transaction
	default void renamePending(long vaultId, String path, String targetPath, String targetName, String targetMimeType, String cloudPath) {
		DocumentMetadataEntity pending = load(vaultId, path);
		moveSubtree(vaultId, path, targetPath, pending.getParentPath(), targetName, targetMimeType, cloudPath);
	}

	/**
	 * Undoes a move of the node with {@code id} that the cloud rejected. The node goes back to {@code SOURCE_PATH}, where
	 * its moves started, together with its descendants. It stays where it is if something has taken that place, or if the
	 * folder there is no longer in the cache, e.g. because a move of that folder has been undone, too. Both folders are
	 * marked for listing again.
	 */
	@Transaction
	default void revertMove(long id, String sourceMimeType) {
		DocumentMetadataEntity moved = loadById(id);
		if (moved == null || moved.getSourcePath() == null) {
			return;
		}
		long vaultId = moved.getVaultId();
		String sourcePath = moved.getSourcePath();
		String sourceParentPath = parentPathOf(sourcePath);
		DocumentMetadataEntity sourceFolder = loadVisible(vaultId, sourceParentPath);
		if (load(vaultId, sourcePath) == null && (sourceParentPath.isEmpty() || (sourceFolder != null && sourceFolder.isFolder()))) {
			moveSubtree(vaultId, moved.getPath(), sourcePath, sourceParentPath, sourcePath.substring(sourcePath.lastIndexOf('/') + 1), sourceMimeType, null);
		}
		clearSourcePath(id);
		updateLastEnumeratedAt(vaultId, moved.getParentPath(), null);
		updateLastEnumeratedAt(vaultId, sourceParentPath, null);
	}

	/**
	 * Drops a change that could not be applied to the cloud and marks the folders it touched for listing again. That
	 * listing undoes a move, unless the moved subtree holds a pending upload, which stays where it was moved to. A node
	 * being created is dropped right away.
	 */
	@Transaction
	default void discardPendingChange(DocumentMetadataEntity pending) {
		long vaultId = pending.getVaultId();
		if (pending.getSourcePath() != null) {
			clearSourcePath(pending.getId());
			updateLastEnumeratedAt(vaultId, parentPathOf(pending.getSourcePath()), null);
		}
		if (pending.isCreating()) {
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
