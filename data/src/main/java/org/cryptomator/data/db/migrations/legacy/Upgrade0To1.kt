package org.cryptomator.data.db.migrations.legacy

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
import org.cryptomator.data.db.migrations.Sql
import org.cryptomator.data.db.migrations.Sql.SqlCreateTableBuilder.ForeignKeyBehaviour
import org.cryptomator.domain.CloudType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade0To1 @Inject constructor() : DatabaseMigration(0, 1) {

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		createCloudEntityTable(db)
		createVaultEntityTable(db)
		createDropboxCloud(db)
		createGoogleDriveCloud(db)
		createLocalStorageCloud(db)
		createOnedriveCloud(db)
	}

	private fun createCloudEntityTable(db: SupportSQLiteDatabase) {
		Sql.createTable("CLOUD_ENTITY") //
			.pre15Id() //
			.requiredText("TYPE") //
			.optionalText("ACCESS_TOKEN") //
			.optionalText("WEBDAV_URL") //
			.optionalText("USERNAME") //
			.optionalText("WEBDAV_CERTIFICATE") //
			.executeOn(db)
	}

	private fun createVaultEntityTable(db: SupportSQLiteDatabase) {
		Sql.createTable("VAULT_ENTITY") //
			.pre15Id() //
			.optionalInt("FOLDER_CLOUD_ID") //
			.optionalText("FOLDER_PATH") //
			.optionalText("FOLDER_NAME") //
			.requiredText("CLOUD_TYPE") //
			.optionalText("PASSWORD") //
			.pre15ForeignKey("FOLDER_CLOUD_ID", "CLOUD_ENTITY", ForeignKeyBehaviour.ON_DELETE_SET_NULL) //
			.executeOn(db)
		Sql.createUniqueIndex("IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID") //
			.on("VAULT_ENTITY") //
			.asc("FOLDER_PATH") //
			.asc("FOLDER_CLOUD_ID") //
			.executeOn(db)
	}

	private fun createDropboxCloud(db: SupportSQLiteDatabase) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 1) //
			.text("TYPE", CloudType.DROPBOX.name) //
			.text("ACCESS_TOKEN", null) //
			.text("WEBDAV_URL", null) //
			.text("USERNAME", null) //
			.text("WEBDAV_CERTIFICATE", null) //
			.executeOn(db)
	}

	private fun createGoogleDriveCloud(db: SupportSQLiteDatabase) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 2) //
			.text("TYPE", CloudType.GOOGLE_DRIVE.name) //
			.text("ACCESS_TOKEN", null) //
			.text("WEBDAV_URL", null) //
			.text("USERNAME", null) //
			.text("WEBDAV_CERTIFICATE", null) //
			.executeOn(db)
	}

	private fun createOnedriveCloud(db: SupportSQLiteDatabase) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 3) //
			.text("TYPE", CloudType.ONEDRIVE.name) //
			.text("ACCESS_TOKEN", null) //
			.text("WEBDAV_URL", null) //
			.text("USERNAME", null) //
			.text("WEBDAV_CERTIFICATE", null) //
			.executeOn(db)
	}

	private fun createLocalStorageCloud(db: SupportSQLiteDatabase) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 4) //
			.text("TYPE", CloudType.LOCAL.name) //
			.text("ACCESS_TOKEN", null) //
			.text("WEBDAV_URL", null) //
			.text("USERNAME", null) //
			.text("WEBDAV_CERTIFICATE", null) //
			.executeOn(db)
	}
}
