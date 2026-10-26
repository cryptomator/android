package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade14To15 @Inject constructor() : DatabaseUpgrade(14, 15) {

	override fun internalMigrate(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			addSharepointColumnsToCloudEntity(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun addSharepointColumnsToCloudEntity(db: SupportSQLiteDatabase) {
		Sql.alterTable("CLOUD_ENTITY").addTextColumn("SHAREPOINT_DRIVE_ID").executeOn(db)
		Sql.alterTable("CLOUD_ENTITY").addTextColumn("SHAREPOINT_DRIVE_NAME").executeOn(db)
	}
}
