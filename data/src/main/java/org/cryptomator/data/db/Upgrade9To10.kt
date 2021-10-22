package org.cryptomator.data.db

import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade9To10 @Inject constructor() : DatabaseUpgrade(9, 10) {

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()

		try {
			Sql.update("VAULT_ENTITY")
				.set("FOLDER_CLOUD_ID", Sql.toString(null))
				.where("FOLDER_CLOUD_ID", Sql.eq(4))
				.executeOn(db)

			Sql.deleteFrom("CLOUD_ENTITY")
				.where("_id", Sql.eq(4))
				.where("TYPE", Sql.eq("LOCAL"))
				.executeOn(db)

			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}
}
