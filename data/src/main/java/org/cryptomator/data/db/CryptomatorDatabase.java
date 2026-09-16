package org.cryptomator.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import org.cryptomator.data.db.dao.CloudDao;
import org.cryptomator.data.db.dao.UpdateCheckDao;
import org.cryptomator.data.db.dao.VaultDao;
import org.cryptomator.data.db.entities.CloudEntity;
import org.cryptomator.data.db.entities.UpdateCheckEntity;
import org.cryptomator.data.db.entities.VaultEntity;

@Database(entities = {CloudEntity.class, UpdateCheckEntity.class, VaultEntity.class}, version = CryptomatorDatabase.VERSION)
public abstract class CryptomatorDatabase extends RoomDatabase {

	static final String NAME = "Cryptomator";

	/**
	 * Deliberately the schema version greenDAO left behind. The tables it created for v14 already match the
	 * entities of this database, so Room adopts an existing v14 file as is instead of rewriting it. Databases
	 * from older app versions are brought up to v14 by the upgrades in {@link DatabaseUpgrades}, which are
	 * therefore the only migrations Room ever has to run.
	 */
	static final int VERSION = 14;

	public abstract CloudDao cloudDao();

	public abstract UpdateCheckDao updateCheckDao();

	public abstract VaultDao vaultDao();
}
