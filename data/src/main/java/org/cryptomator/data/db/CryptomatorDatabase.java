package org.cryptomator.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import org.cryptomator.data.db.dao.CachedFileDao;
import org.cryptomator.data.db.dao.CloudDao;
import org.cryptomator.data.db.dao.DocumentMetadataDao;
import org.cryptomator.data.db.dao.UpdateCheckDao;
import org.cryptomator.data.db.dao.VaultDao;
import org.cryptomator.data.db.entities.CachedFileEntity;
import org.cryptomator.data.db.entities.CloudEntity;
import org.cryptomator.data.db.entities.DocumentMetadataEntity;
import org.cryptomator.data.db.entities.UpdateCheckEntity;
import org.cryptomator.data.db.entities.VaultEntity;

@Database(entities = {CloudEntity.class, UpdateCheckEntity.class, VaultEntity.class, DocumentMetadataEntity.class, CachedFileEntity.class}, version = CryptomatorDatabase.VERSION)
public abstract class CryptomatorDatabase extends RoomDatabase {

	static final String NAME = "Cryptomator";

	/**
	 * Continues greenDAO's version numbering, which ended at v14. The tables greenDAO created for v14 match
	 * Room's v14 schema, so the upgrades in {@link DatabaseUpgrades} take a greenDAO file of any version to this one.
	 */
	static final int VERSION = 16;

	public abstract CachedFileDao cachedFileDao();

	public abstract CloudDao cloudDao();

	public abstract DocumentMetadataDao documentMetadataDao();

	public abstract UpdateCheckDao updateCheckDao();

	public abstract VaultDao vaultDao();
}
