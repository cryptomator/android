package org.cryptomator.data.db.migrations.legacy

import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
import org.cryptomator.data.db.migrations.Sql
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
internal class Upgrade6To7 @Inject constructor() : DatabaseMigration(6, 7) {

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			changeUpdateEntityToSupportSha256Verification(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun changeUpdateEntityToSupportSha256Verification(db: SupportSQLiteDatabase) {
		Sql.alterTable("UPDATE_CHECK_ENTITY").renameTo("UPDATE_CHECK_ENTITY_OLD").executeOn(db)

		Sql.createTable("UPDATE_CHECK_ENTITY") //
			.pre15Id() //
			.optionalText("LICENSE_TOKEN") //
			.optionalText("RELEASE_NOTE") //
			.optionalText("VERSION") //
			.optionalText("URL_TO_APK") //
			.optionalText("APK_SHA256") //
			.optionalText("URL_TO_RELEASE_NOTE") //
			.executeOn(db)

		try {
			Sql.insertInto("UPDATE_CHECK_ENTITY") //
				.select("_id", "LICENSE_TOKEN", "RELEASE_NOTE", "VERSION", "URL_TO_APK", "URL_TO_RELEASE_NOTE") //
				.columns("_id", "LICENSE_TOKEN", "RELEASE_NOTE", "VERSION", "URL_TO_APK", "URL_TO_RELEASE_NOTE") //
				.from("UPDATE_CHECK_ENTITY_OLD") //
				.executeOn(db)
		} catch (e: SQLiteException) {
			Timber.tag("Upgrade6To7").e(e, "Failed to recover data from old update check entity, insert new initial entry. More details in #336")
			tryToRecoverFromSQLiteException(db)
		}

		Sql.dropTable("UPDATE_CHECK_ENTITY_OLD").executeOn(db)
	}

	fun tryToRecoverFromSQLiteException(db: SupportSQLiteDatabase) {
		var licenseToken: String? = null

		try {
			Sql.query("UPDATE_CHECK_ENTITY_OLD").executeOn(db).use {
				if (it.moveToNext()) {
					licenseToken = it.getString(it.getColumnIndex("LICENSE_TOKEN"))
				}
			}
		} catch (e: SQLiteException) {
			Timber.tag("Upgrade6To7").e(e, "Failed to recover license token while recovery, clear license token if used.")
		}

		Sql.insertInto("UPDATE_CHECK_ENTITY") //
			.integer("_id", 1) //
			.text("LICENSE_TOKEN", licenseToken) //
			.text("RELEASE_NOTE", null) //
			.text("VERSION", null) //
			.text("URL_TO_APK", null) //
			.text("APK_SHA256", null) //
			.text("URL_TO_RELEASE_NOTE", null) //
			.executeOn(db)
	}
}
