package org.cryptomator.data.db;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import timber.log.Timber;

abstract class DatabaseUpgrade extends Migration {

	DatabaseUpgrade(int from, int to) {
		super(from, to);
	}

	@Override
	public final void migrate(@NonNull SupportSQLiteDatabase db) {
		Timber.tag("DatabaseUpgrade").i("Running %s (%d -> %d)", getClass().getSimpleName(), startVersion, endVersion);
		internalMigrate(db);
	}

	protected abstract void internalMigrate(SupportSQLiteDatabase db);

}
