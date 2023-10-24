package org.cryptomator.data.db

import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber


abstract class DatabaseAutoMigrationSpec : AutoMigrationSpec {

	final override fun onPostMigrate(db: SupportSQLiteDatabase) {
		Timber.tag("DatabaseMigration").i("Ran automatic migration %s", javaClass.simpleName)
		onPostMigrateInternal(db)
	}

	protected open fun onPostMigrateInternal(db: SupportSQLiteDatabase) {}

}
