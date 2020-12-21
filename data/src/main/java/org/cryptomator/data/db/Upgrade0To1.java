package org.cryptomator.data.db;

import org.cryptomator.domain.CloudType;
import org.greenrobot.greendao.database.Database;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ForeignKeyBehaviour.ON_DELETE_SET_NULL;
import static org.cryptomator.data.db.Sql.createTable;
import static org.cryptomator.data.db.Sql.createUniqueIndex;
import static org.cryptomator.data.db.Sql.insertInto;

@Singleton
class Upgrade0To1 extends DatabaseUpgrade {

	@Inject
	public Upgrade0To1() {
		super(0, 1);
	}

	@Override
	protected void internalApplyTo(Database db, int origin) {
		createCloudEntityTable(db);
		createVaultEntityTable(db);

		createDropboxCloud(db);
		createGoogleDriveCloud(db);
		createLocalStorageCloud(db);
		createOnedriveCloud(db);
	}

	private void createCloudEntityTable(Database db) {
		createTable("CLOUD_ENTITY") //
				.id() //
				.requiredText("TYPE") //
				.optionalText("ACCESS_TOKEN") //
				.optionalText("WEBDAV_URL") //
				.optionalText("USERNAME") //
				.optionalText("WEBDAV_CERTIFICATE") //
				.executeOn(db);
	}

	private void createVaultEntityTable(Database db) {
		createTable("VAULT_ENTITY") //
				.id() //
				.optionalInt("FOLDER_CLOUD_ID") //
				.optionalText("FOLDER_PATH") //
				.optionalText("FOLDER_NAME") //
				.requiredText("CLOUD_TYPE") //
				.optionalText("PASSWORD") //
				.foreignKey("FOLDER_CLOUD_ID", "CLOUD_ENTITY", ON_DELETE_SET_NULL) //
				.executeOn(db);

		createUniqueIndex("IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID") //
				.on("VAULT_ENTITY") //
				.asc("FOLDER_PATH") //
				.asc("FOLDER_CLOUD_ID") //
				.executeOn(db);
	}

	private void createDropboxCloud(Database db) {
		insertInto("CLOUD_ENTITY") //
				.integer("_id", 1) //
				.text("TYPE", CloudType.DROPBOX.name()) //
				.text("ACCESS_TOKEN", null) //
				.text("WEBDAV_URL", null) //
				.text("USERNAME", null) //
				.text("WEBDAV_CERTIFICATE", null) //
				.executeOn(db);
	}

	private void createGoogleDriveCloud(Database db) {
		insertInto("CLOUD_ENTITY") //
				.integer("_id", 2) //
				.text("TYPE", CloudType.GOOGLE_DRIVE.name()) //
				.text("ACCESS_TOKEN", null) //
				.text("WEBDAV_URL", null) //
				.text("USERNAME", null) //
				.text("WEBDAV_CERTIFICATE", null) //
				.executeOn(db);
	}

	private void createOnedriveCloud(Database db) {
		insertInto("CLOUD_ENTITY") //
				.integer("_id", 3) //
				.text("TYPE", CloudType.ONEDRIVE.name()) //
				.text("ACCESS_TOKEN", null) //
				.text("WEBDAV_URL", null) //
				.text("USERNAME", null) //
				.text("WEBDAV_CERTIFICATE", null) //
				.executeOn(db);
	}

	private void createLocalStorageCloud(Database db) {
		insertInto("CLOUD_ENTITY") //
				.integer("_id", 4) //
				.text("TYPE", CloudType.LOCAL.name()) //
				.text("ACCESS_TOKEN", null) //
				.text("WEBDAV_URL", null) //
				.text("USERNAME", null) //
				.text("WEBDAV_CERTIFICATE", null) //
				.executeOn(db);
	}
}
