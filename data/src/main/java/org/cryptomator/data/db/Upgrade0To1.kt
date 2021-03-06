package org.cryptomator.data.db

import org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ForeignKeyBehaviour
import org.cryptomator.domain.CloudType
import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade0To1 @Inject constructor() : DatabaseUpgrade(0, 1) {

	override fun internalApplyTo(db: Database, origin: Int) {
		createCloudEntityTable(db)
		createVaultEntityTable(db)
		createDropboxCloud(db)
		createGoogleDriveCloud(db)
		createLocalStorageCloud(db)
		createOnedriveCloud(db)
	}

	private fun createCloudEntityTable(db: Database) {
		Sql.createTable("CLOUD_ENTITY") //
			.id() //
			.requiredText("TYPE") //
			.optionalText("ACCESS_TOKEN") //
			.optionalText("WEBDAV_URL") //
			.optionalText("USERNAME") //
			.optionalText("WEBDAV_CERTIFICATE") //
			.executeOn(db)
	}

	private fun createVaultEntityTable(db: Database) {
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

	private fun createDropboxCloud(db: Database) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 1) //
			.text("TYPE", CloudType.DROPBOX.name) //
			.text("ACCESS_TOKEN", null) //
			.text("WEBDAV_URL", null) //
			.text("USERNAME", null) //
			.text("WEBDAV_CERTIFICATE", null) //
			.executeOn(db)
	}

	private fun createGoogleDriveCloud(db: Database) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 2) //
			.text("TYPE", CloudType.GOOGLE_DRIVE.name) //
			.text("ACCESS_TOKEN", null) //
			.text("WEBDAV_URL", null) //
			.text("USERNAME", null) //
			.text("WEBDAV_CERTIFICATE", null) //
			.executeOn(db)
	}

	private fun createOnedriveCloud(db: Database) {
		Sql.insertInto("CLOUD_ENTITY") //
			.integer("_id", 3) //
			.text("TYPE", CloudType.ONEDRIVE.name) //
			.text("ACCESS_TOKEN", null) //
			.text("WEBDAV_URL", null) //
			.text("USERNAME", null) //
			.text("WEBDAV_CERTIFICATE", null) //
			.executeOn(db)
	}

	private fun createLocalStorageCloud(db: Database) {
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
