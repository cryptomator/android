package org.cryptomator.data.db

import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade6To7 @Inject constructor() : DatabaseUpgrade(6, 7) {

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()
		try {
			changeUpdateEntityToSupportSha256Verification(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun changeUpdateEntityToSupportSha256Verification(db: Database) {
		Sql.alterTable("UPDATE_CHECK_ENTITY").renameTo("UPDATE_CHECK_ENTITY_OLD").executeOn(db)

		Sql.createTable("UPDATE_CHECK_ENTITY") //
				.id() //
				.optionalText("LICENSE_TOKEN") //
				.optionalText("RELEASE_NOTE") //
				.optionalText("VERSION") //
				.optionalText("URL_TO_APK") //
				.optionalText("APK_SHA256") //
				.optionalText("URL_TO_RELEASE_NOTE") //
				.executeOn(db)

		Sql.insertInto("UPDATE_CHECK_ENTITY") //
				.select("_id", "LICENSE_TOKEN", "RELEASE_NOTE", "VERSION", "URL_TO_APK", "URL_TO_RELEASE_NOTE") //
				.columns("_id", "LICENSE_TOKEN", "RELEASE_NOTE", "VERSION", "URL_TO_APK", "URL_TO_RELEASE_NOTE") //
				.from("UPDATE_CHECK_ENTITY_OLD") //
				.executeOn(db)

		Sql.dropTable("UPDATE_CHECK_ENTITY_OLD").executeOn(db)
	}
}
