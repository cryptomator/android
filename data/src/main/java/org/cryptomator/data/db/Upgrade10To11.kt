package org.cryptomator.data.db

import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade10To11 @Inject constructor() : DatabaseUpgrade(10, 11) {
	private val defaultThreshold = 220
	private val defaultVaultFormat = 8
	private val onedriveCloudId = 3L

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()
		try {
			addFormatAndShorteningToDbEntity(db)
			addDefaultFormatAndShorteningThresholdToVaults(db)

			deleteOnedriveCloudIfNotSetUp(db)

			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun addFormatAndShorteningToDbEntity(db: Database) {
		Sql.alterTable("VAULT_ENTITY").renameTo("VAULT_ENTITY_OLD").executeOn(db)
		Sql.createTable("VAULT_ENTITY") //
			.id() //
			.optionalInt("FOLDER_CLOUD_ID") //
			.optionalText("FOLDER_PATH") //
			.optionalText("FOLDER_NAME") //
			.requiredText("CLOUD_TYPE") //
			.optionalText("PASSWORD") //
			.optionalInt("POSITION") //
			.optionalInt("FORMAT") //
			.optionalInt("SHORTENING_THRESHOLD") //
			.foreignKey("FOLDER_CLOUD_ID", "CLOUD_ENTITY", Sql.SqlCreateTableBuilder.ForeignKeyBehaviour.ON_DELETE_SET_NULL) //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.select("_id", "FOLDER_CLOUD_ID", "FOLDER_PATH", "FOLDER_NAME", "PASSWORD", "POSITION", "CLOUD_ENTITY.TYPE") //
			.columns("_id", "FOLDER_CLOUD_ID", "FOLDER_PATH", "FOLDER_NAME", "PASSWORD", "POSITION", "CLOUD_TYPE") //
			.from("VAULT_ENTITY_OLD") //
			.join("CLOUD_ENTITY", "VAULT_ENTITY_OLD.FOLDER_CLOUD_ID") //
			.executeOn(db)

		Sql.dropIndex("IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID").executeOn(db)

		Sql.createUniqueIndex("IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID") //
			.on("VAULT_ENTITY") //
			.asc("FOLDER_PATH") //
			.asc("FOLDER_CLOUD_ID") //
			.executeOn(db)

		Sql.dropTable("VAULT_ENTITY_OLD").executeOn(db)
	}


	private fun addDefaultFormatAndShorteningThresholdToVaults(db: Database) {
		Sql.update("VAULT_ENTITY")
			.set("FORMAT", Sql.toInteger(defaultVaultFormat))
			.set("SHORTENING_THRESHOLD", Sql.toInteger(defaultThreshold))
			.executeOn(db)
	}

	private fun deleteOnedriveCloudIfNotSetUp(db: Database) {
		Sql.deleteFrom("CLOUD_ENTITY")
			.where("_id", Sql.eq(onedriveCloudId))
			.where("TYPE", Sql.eq("ONEDRIVE"))
			.where("ACCESS_TOKEN", Sql.isNull())
			.executeOn(db)
	}
}
