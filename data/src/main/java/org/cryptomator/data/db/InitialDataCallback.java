package org.cryptomator.data.db;

import androidx.annotation.NonNull;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.cryptomator.domain.CloudType;

import timber.log.Timber;

/**
 * Seeds a freshly created database with the rows every installation starts out with. Under greenDAO these
 * were inserted by the upgrade from v0 to v1 and partly deleted again by later upgrades, so this is the state
 * an install that walked the whole upgrade chain ended up in.
 */
class InitialDataCallback extends RoomDatabase.Callback {

	@Override
	public void onCreate(@NonNull SupportSQLiteDatabase db) {
		Timber.tag("Database").i("Create v%d", CryptomatorDatabase.VERSION);

		createCloud(db, 1, CloudType.DROPBOX);
		createCloud(db, 2, CloudType.GOOGLE_DRIVE);

		Sql.insertInto("UPDATE_CHECK_ENTITY") //
				.integer("_id", 1) //
				.executeOn(db);
	}

	private void createCloud(SupportSQLiteDatabase db, int id, CloudType type) {
		Sql.insertInto("CLOUD_ENTITY") //
				.integer("_id", id) //
				.text("TYPE", type.name()) //
				.executeOn(db);
	}
}
