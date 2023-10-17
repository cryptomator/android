package org.cryptomator.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ForeignKeyBehaviour
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade3To4 @Inject constructor() : Migration(3, 4) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			addPositionToVaultSchema(db)
			initVaultPositionUsingCurrentSortOrder(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun addPositionToVaultSchema(db: SupportSQLiteDatabase) {
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

	private fun initVaultPositionUsingCurrentSortOrder(db: SupportSQLiteDatabase) {
		Sql.query("VAULT_ENTITY").executeOn(db).use {
			while (it.moveToNext()) {
				Sql.update("VAULT_ENTITY")
					.where("_id", Sql.eq(it.getLong(it.getColumnIndex("_id"))))
					.set("POSITION", Sql.toInteger(it.position))
					.executeOn(db)
			}
		}
	}
}
