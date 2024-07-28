package org.cryptomator.data.db.migrations.legacy

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
import org.cryptomator.data.db.migrations.Sql
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade4To5 @Inject constructor() : DatabaseMigration(4, 5) {

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			changeWebdavUrlInCloudEntityToUrl(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun changeWebdavUrlInCloudEntityToUrl(db: SupportSQLiteDatabase) {
		Sql.alterTable("CLOUD_ENTITY").renameTo("CLOUD_ENTITY_OLD").executeOn(db)

		Sql.createTable("CLOUD_ENTITY") //
			.pre14Id() //
			.requiredText("TYPE") //
			.optionalText("ACCESS_TOKEN") //
			.optionalText("URL") //
			.optionalText("USERNAME") //
			.optionalText("WEBDAV_CERTIFICATE") //
			.executeOn(db)

		Sql.insertInto("CLOUD_ENTITY") //
			.select("_id", "TYPE", "ACCESS_TOKEN", "WEBDAV_URL", "USERNAME", "WEBDAV_CERTIFICATE") //
			.columns("_id", "TYPE", "ACCESS_TOKEN", "URL", "USERNAME", "WEBDAV_CERTIFICATE") //
			.from("CLOUD_ENTITY_OLD") //
			.executeOn(db)

		recreateVaultEntity(db)

		Sql.dropTable("CLOUD_ENTITY_OLD").executeOn(db)
	}

	private fun recreateVaultEntity(db: SupportSQLiteDatabase) {
		Sql.alterTable("VAULT_ENTITY").renameTo("VAULT_ENTITY_OLD").executeOn(db)
		Sql.createTable("VAULT_ENTITY") //
			.pre14Id() //
			.optionalInt("FOLDER_CLOUD_ID") //
			.optionalText("FOLDER_PATH") //
			.optionalText("FOLDER_NAME") //
			.requiredText("CLOUD_TYPE") //
			.optionalText("PASSWORD") //
			.optionalInt("POSITION") //
			.pre14ForeignKey("FOLDER_CLOUD_ID", "CLOUD_ENTITY", Sql.SqlCreateTableBuilder.ForeignKeyBehaviour.ON_DELETE_SET_NULL) //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.select("_id", "FOLDER_CLOUD_ID", "FOLDER_PATH", "FOLDER_NAME", "PASSWORD", "POSITION", "CLOUD_ENTITY.TYPE") //
			.columns("_id", "FOLDER_CLOUD_ID", "FOLDER_PATH", "FOLDER_NAME", "PASSWORD", "POSITION", "CLOUD_TYPE") //
			.from("VAULT_ENTITY_OLD") //
			.pre14Join("CLOUD_ENTITY", "VAULT_ENTITY_OLD.FOLDER_CLOUD_ID") //
			.executeOn(db)

		Sql.dropIndex("IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID").executeOn(db)

		Sql.createUniqueIndex("IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID") //
			.on("VAULT_ENTITY") //
			.asc("FOLDER_PATH") //
			.asc("FOLDER_CLOUD_ID") //
			.executeOn(db)

		Sql.dropTable("VAULT_ENTITY_OLD").executeOn(db)
	}
}