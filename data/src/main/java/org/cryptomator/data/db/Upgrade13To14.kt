package org.cryptomator.data.db

import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade13To14 @Inject constructor() : DatabaseUpgrade(13, 14) {

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()
		try {
			createUploadCheckpointTable(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun createUploadCheckpointTable(db: Database) {
		Sql.createTable("UPLOAD_CHECKPOINT_ENTITY") //
			.id() //
			.requiredInt("VAULT_ID") //
			.requiredText("TYPE") //
			.requiredText("TARGET_FOLDER_PATH") //
			.optionalText("SOURCE_FOLDER_URI") //
			.optionalText("SOURCE_FOLDER_NAME") //
			.optionalText("PENDING_FILE_URIS") //
			.requiredText("COMPLETED_FILES") //
			.requiredInt("TOTAL_FILE_COUNT") //
			.requiredInt("TIMESTAMP") //
			.executeOn(db)

		Sql.createUniqueIndex("IDX_UPLOAD_CHECKPOINT_ENTITY_VAULT_ID") //
			.on("UPLOAD_CHECKPOINT_ENTITY") //
			.asc("VAULT_ID") //
			.executeOn(db)
	}
}
