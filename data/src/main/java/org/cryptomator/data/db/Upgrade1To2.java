package org.cryptomator.data.db;

import static org.cryptomator.data.db.Sql.createTable;
import static org.cryptomator.data.db.Sql.insertInto;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.greenrobot.greendao.database.Database;

@Singleton
class Upgrade1To2 extends DatabaseUpgrade {

	@Inject
	Upgrade1To2() {
		super(1, 2);
	}

	@Override
	protected void internalApplyTo(Database db, int origin) {
		createUpdateCheckTable(db);
		createInitialUpdateStatus(db);
	}

	private void createUpdateCheckTable(Database db) {
		db.beginTransaction();
		try {
			createTable("UPDATE_CHECK_ENTITY") //
					.id() //
					.optionalText("LICENSE_TOKEN") //
					.optionalText("RELEASE_NOTE") //
					.optionalText("VERSION") //
					.optionalText("URL_TO_APK") //
					.optionalText("URL_TO_RELEASE_NOTE") //
					.executeOn(db);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	private void createInitialUpdateStatus(Database db) {
		insertInto("UPDATE_CHECK_ENTITY") //
				.integer("_id", 1) //
				.bool("LICENSE_TOKEN", null) //
				.text("RELEASE_NOTE", null) //
				.text("VERSION", null) //
				.text("URL_TO_APK", null) //
				.text("URL_TO_RELEASE_NOTE", null) //
				.executeOn(db);
	}
}
