package org.cryptomator.data.db

import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade7To8 @Inject constructor() : DatabaseUpgrade(7, 8) {

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()
		try {
			dropS3Vaults(db)
			dropS3Clouds(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun dropS3Vaults(db: Database) {
		Sql.deleteFrom("VAULT_ENTITY") //
			.where("CLOUD_TYPE", Sql.eq("S3"))
			.executeOn(db)
	}

	private fun dropS3Clouds(db: Database) {
		Sql.deleteFrom("CLOUD_ENTITY") //
			.where("TYPE", Sql.eq("S3"))
			.executeOn(db)
	}
}
