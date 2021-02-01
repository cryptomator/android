package org.cryptomator.data.db

import org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ForeignKeyBehaviour
import org.cryptomator.data.db.entities.CloudEntityDao
import org.cryptomator.data.db.entities.VaultEntityDao
import org.greenrobot.greendao.database.Database
import org.greenrobot.greendao.internal.DaoConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade3To4 @Inject constructor() : DatabaseUpgrade(3, 4) {

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()
		try {
			addPositionToVaultSchema(db)
			initVaultPositionUsingCurrentSortOrder(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun addPositionToVaultSchema(db: Database) {
		Sql.alterTable("VAULT_ENTITY").renameTo("VAULT_ENTITY_OLD").executeOn(db)
		Sql.createTable("VAULT_ENTITY") //
				.id() //
				.optionalInt("FOLDER_CLOUD_ID") //
				.optionalText("FOLDER_PATH") //
				.optionalText("FOLDER_NAME") //
				.requiredText("CLOUD_TYPE") //
				.optionalText("PASSWORD") //
				.optionalInt("POSITION") //
				.foreignKey("FOLDER_CLOUD_ID", "CLOUD_ENTITY", ForeignKeyBehaviour.ON_DELETE_SET_NULL) //
				.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
				.select("_id", "FOLDER_CLOUD_ID", "FOLDER_PATH", "FOLDER_NAME", "PASSWORD", "CLOUD_ENTITY.TYPE") //
				.columns("_id", "FOLDER_CLOUD_ID", "FOLDER_PATH", "FOLDER_NAME", "PASSWORD", "CLOUD_TYPE") //
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

	private fun initVaultPositionUsingCurrentSortOrder(db: Database) {
		CloudEntityDao(DaoConfig(db, VaultEntityDao::class.java)) //
				.loadAll() //
				.map {
					Sql.update("VAULT_ENTITY") //
							.where("_id", Sql.eq(it.id)) //
							.set("POSITION", Sql.toInteger(it.id)) //
							.executeOn(db)
				}
	}
}
