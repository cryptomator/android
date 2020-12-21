package org.cryptomator.data.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.cryptomator.data.db.entities.DaoMaster;
import org.greenrobot.greendao.database.Database;

import javax.inject.Inject;
import javax.inject.Singleton;

import timber.log.Timber;

import static org.cryptomator.data.db.entities.DaoMaster.SCHEMA_VERSION;

@Singleton
class DatabaseFactory extends DaoMaster.OpenHelper {

	private static final String DATABASE_NAME = "Cryptomator";

	private final DatabaseUpgrades databaseUpgrades;

	@Inject
	public DatabaseFactory(Context context, DatabaseUpgrades databaseUpgrades) {
		super(context, DATABASE_NAME);
		this.databaseUpgrades = databaseUpgrades;
	}

	@Override
	public void onConfigure(SQLiteDatabase db) {
		super.onConfigure(db);

		Timber.tag("Database").i("Configure v%d", db.getVersion());

		if (!db.isReadOnly()) {
			db.setForeignKeyConstraintsEnabled(true);
		}
	}

	@Override
	public void onCreate(Database db) {
		Timber.tag("Database").i("Create v%s", SCHEMA_VERSION);
		databaseUpgrades.getUpgrade(0, SCHEMA_VERSION).applyTo(db, 0);
	}

	@Override
	public void onUpgrade(Database db, int oldVersion, int newVersion) {
		Timber.tag("Database").i("Upgrade v" + oldVersion + " to v" + newVersion);
		databaseUpgrades.getUpgrade(oldVersion, newVersion).applyTo(db, oldVersion);
	}

	@Override
	public void onOpen(SQLiteDatabase db) {
		super.onOpen(db);
		Timber.tag("Database").i("Open v%s", db.getVersion());
	}
}
