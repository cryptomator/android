package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade16To17 @Inject constructor() : DatabaseUpgrade(16, 17) {

	override fun internalMigrate(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			addWebDavProviderToCloudEntity(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun addWebDavProviderToCloudEntity(db: SupportSQLiteDatabase) {
		Sql.alterTable("CLOUD_ENTITY").addTextColumn("WEBDAV_PROVIDER").executeOn(db)
	}
}
