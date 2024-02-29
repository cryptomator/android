package org.cryptomator.data.db.migrations.legacy

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
import org.cryptomator.data.db.migrations.Sql
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade1To2 @Inject constructor() : DatabaseMigration(1, 2) {

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		createUpdateCheckTable(db)
		createInitialUpdateStatus(db)
	}

	private fun createUpdateCheckTable(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			Sql.createTable("UPDATE_CHECK_ENTITY") //
				.pre14Id() //
				.optionalText("LICENSE_TOKEN") //
				.optionalText("RELEASE_NOTE") //
				.optionalText("VERSION") //
				.optionalText("URL_TO_APK") //
				.optionalText("URL_TO_RELEASE_NOTE") //
				.executeOn(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun createInitialUpdateStatus(db: SupportSQLiteDatabase) {
		Sql.insertInto("UPDATE_CHECK_ENTITY") //
			.integer("_id", 1) //
			.text("LICENSE_TOKEN", null) //
			.text("RELEASE_NOTE", null) //
			.text("VERSION", null) //
			.text("URL_TO_APK", null) //
			.text("URL_TO_RELEASE_NOTE", null) //
			.executeOn(db)
	}
}
