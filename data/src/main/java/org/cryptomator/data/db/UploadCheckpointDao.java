package org.cryptomator.data.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.cryptomator.data.db.entities.UploadCheckpointEntity;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UploadCheckpointDao {

	private static final String TABLE_NAME = "UPLOAD_CHECKPOINT_ENTITY";

	private final DatabaseFactory databaseFactory;

	@Inject
	public UploadCheckpointDao(DatabaseFactory databaseFactory) {
		this.databaseFactory = databaseFactory;
	}

	private SQLiteDatabase getDb() {
		return databaseFactory.getWritableDatabase();
	}

	public long insertOrReplace(UploadCheckpointEntity entity) {
		ContentValues values = toContentValues(entity);
		return getDb().insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
	}

	public UploadCheckpointEntity findByVaultId(long vaultId) {
		try (Cursor cursor = getDb().query(TABLE_NAME, null, "VAULT_ID = ?",
				new String[]{String.valueOf(vaultId)}, null, null, null)) {
			if (cursor.moveToFirst()) {
				return fromCursor(cursor);
			}
			return null;
		}
	}

	public void deleteByVaultId(long vaultId) {
		getDb().delete(TABLE_NAME, "VAULT_ID = ?", new String[]{String.valueOf(vaultId)});
	}

	public Set<Long> findAllVaultIdsWithCheckpoints() {
		Set<Long> result = new HashSet<>();
		try (Cursor cursor = getDb().query(TABLE_NAME, new String[]{"VAULT_ID"},
				null, null, null, null, null)) {
			while (cursor.moveToNext()) {
				result.add(cursor.getLong(0));
			}
		}
		return result;
	}

	public void updateCompletedFiles(long vaultId, String completedFilesJson) {
		ContentValues values = new ContentValues();
		values.put("COMPLETED_FILES", completedFilesJson);
		getDb().update(TABLE_NAME, values, "VAULT_ID = ?", new String[]{String.valueOf(vaultId)});
	}

	private ContentValues toContentValues(UploadCheckpointEntity entity) {
		ContentValues values = new ContentValues();
		values.put("VAULT_ID", entity.getVaultId());
		values.put("TYPE", entity.getType());
		values.put("TARGET_FOLDER_PATH", entity.getTargetFolderPath());
		values.put("SOURCE_FOLDER_URI", entity.getSourceFolderUri());
		values.put("SOURCE_FOLDER_NAME", entity.getSourceFolderName());
		values.put("PENDING_FILE_URIS", entity.getPendingFileUris());
		values.put("COMPLETED_FILES", entity.getCompletedFiles());
		values.put("TOTAL_FILE_COUNT", entity.getTotalFileCount());
		values.put("TIMESTAMP", entity.getTimestamp());
		values.put("REPLACING", entity.isReplacing() ? 1 : 0);
		return values;
	}

	private UploadCheckpointEntity fromCursor(Cursor cursor) {
		UploadCheckpointEntity entity = new UploadCheckpointEntity();
		entity.setId(cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
		entity.setVaultId(cursor.getLong(cursor.getColumnIndexOrThrow("VAULT_ID")));
		entity.setType(cursor.getString(cursor.getColumnIndexOrThrow("TYPE")));
		entity.setTargetFolderPath(cursor.getString(cursor.getColumnIndexOrThrow("TARGET_FOLDER_PATH")));
		entity.setSourceFolderUri(cursor.getString(cursor.getColumnIndexOrThrow("SOURCE_FOLDER_URI")));
		entity.setSourceFolderName(cursor.getString(cursor.getColumnIndexOrThrow("SOURCE_FOLDER_NAME")));
		entity.setPendingFileUris(cursor.getString(cursor.getColumnIndexOrThrow("PENDING_FILE_URIS")));
		entity.setCompletedFiles(cursor.getString(cursor.getColumnIndexOrThrow("COMPLETED_FILES")));
		entity.setTotalFileCount(cursor.getInt(cursor.getColumnIndexOrThrow("TOTAL_FILE_COUNT")));
		entity.setTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow("TIMESTAMP")));
		entity.setReplacing(cursor.getInt(cursor.getColumnIndexOrThrow("REPLACING")) != 0);
		return entity;
	}
}
