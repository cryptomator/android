package org.cryptomator.data.db

import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade10To11 @Inject constructor() : DatabaseUpgrade(10, 11) {

	private val onedriveCloudId = 3L

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()
		try {
			deleteOnedriveCloudIfNotSetUp(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun deleteOnedriveCloudIfNotSetUp(db: Database) {
		Sql.deleteFrom("CLOUD_ENTITY")
			.where("_id", Sql.eq(onedriveCloudId))
			.where("TYPE", Sql.eq("ONEDRIVE"))
			.where("ACCESS_TOKEN", Sql.isNull())
			.executeOn(db)
	}
}
