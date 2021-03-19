package org.cryptomator.data.db

import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade4To5 @Inject constructor() : DatabaseUpgrade(4, 5) {

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()
		try {
			changeWebdavUrlInCloudEntityToUrl(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun changeWebdavUrlInCloudEntityToUrl(db: Database) {
		Sql.alterTable("CLOUD_ENTITY").renameTo("CLOUD_ENTITY_OLD").executeOn(db)

		Sql.createTable("CLOUD_ENTITY") //
				.id() //
				.requiredText("TYPE") //
				.optionalText("ACCESS_TOKEN") //
				.optionalText("URL") //
				.optionalText("USERNAME") //
				.optionalText("WEBDAV_CERTIFICATE") //
				.executeOn(db);

		Sql.insertInto("CLOUD_ENTITY") //
				.select("_id", "TYPE", "ACCESS_TOKEN", "WEBDAV_URL", "USERNAME", "WEBDAV_CERTIFICATE") //
				.columns("_id", "TYPE", "ACCESS_TOKEN", "URL", "USERNAME", "WEBDAV_CERTIFICATE") //
				.from("CLOUD_ENTITY_OLD") //
				.executeOn(db)

		Sql.dropTable("CLOUD_ENTITY_OLD").executeOn(db)
	}
}
