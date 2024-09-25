package org.cryptomator.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber


abstract class DatabaseMigration(startVersion: Int, endVersion: Int) : Migration(startVersion, endVersion) {

	final override fun migrate(database: SupportSQLiteDatabase) {
		Timber.tag("DatabaseMigration").i("Running %s (%d -> %d)", javaClass.simpleName, startVersion, endVersion)
		require(database.foreignKeyConstraintsEnabled)
		migrateInternal(database)
	}

	protected abstract fun migrateInternal(db: SupportSQLiteDatabase)

}
