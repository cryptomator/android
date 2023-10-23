package org.cryptomator.data.db.migrations.legacy

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
import org.cryptomator.data.db.migrations.Sql
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade7To8 @Inject constructor() : DatabaseMigration(7, 8) {

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			dropS3Vaults(db)
			dropS3Clouds(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun dropS3Vaults(db: SupportSQLiteDatabase) {
		Sql.deleteFrom("VAULT_ENTITY") //
			.where("CLOUD_TYPE", Sql.eq("S3"))
			.executeOn(db)
	}

	private fun dropS3Clouds(db: SupportSQLiteDatabase) {
		Sql.deleteFrom("CLOUD_ENTITY") //
			.where("TYPE", Sql.eq("S3"))
			.executeOn(db)
	}
}
