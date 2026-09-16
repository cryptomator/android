package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ForeignKeyBehaviour
import org.cryptomator.domain.CloudType

/**
 * The schema greenDAO created for v1, which the app itself no longer creates: Room creates the current
 * schema directly and only the upgrades from v1 onwards are still needed. Migration tests use this to
 * build a database as old as the oldest one still in the field.
 */
internal object LegacyDatabaseV1 {

	fun createOn(db: SupportSQLiteDatabase) {
		createCloudEntityTable(db)
		createVaultEntityTable(db)
		createCloud(db, 1, CloudType.DROPBOX)
		createCloud(db, 2, CloudType.GOOGLE_DRIVE)
		createCloud(db, 3, CloudType.ONEDRIVE)
		createCloud(db, 4, CloudType.LOCAL)
	}

	private fun createCloudEntityTable(db: SupportSQLiteDatabase) {
		Sql.createTable("CLOUD_ENTITY") //
			.id() //
			.requiredText("TYPE") //
			.optionalText("ACCESS_TOKEN") //
			.optionalText("WEBDAV_URL") //
			.optionalText("USERNAME") //
			.optionalText("WEBDAV_CERTIFICATE") //
			.executeOn(db)
	}

	private fun createVaultEntityTable(db: SupportSQLiteDatabase) {
		Sql.createTable("VAULT_ENTITY") //
			.id() //
			.optionalInt("FOLDER_CLOUD_ID") //
			.optionalText("FOLDER_PATH") //
			.optionalText("FOLDER_NAME") //
			.requiredText("CLOUD_TYPE") //
			.optionalText("PASSWORD") //
			.foreignKey("FOLDER_CLOUD_ID", "CLOUD_ENTITY", ForeignKeyBehaviour.ON_DELETE_SET_NULL) //
			.executeOn(db)
		Sql.createUniqueIndex("IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID") //
			.on("VAULT_ENTITY") //
			.asc("FOLDER_PATH") //
			.asc("FOLDER_CLOUD_ID") //
			.executeOn(db)
	}

	private fun createCloud(db: SupportSQLiteDatabase, id: Int, type: CloudType) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", id) //
			.text("TYPE", type.name) //
			.text("ACCESS_TOKEN", null) //
			.text("WEBDAV_URL", null) //
			.text("USERNAME", null) //
			.text("WEBDAV_CERTIFICATE", null) //
			.executeOn(db)
	}
}
