package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade17To18 @Inject constructor() : DatabaseUpgrade(17, 18) {

	override fun internalMigrate(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			createUploadCheckpointEntity(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun createUploadCheckpointEntity(db: SupportSQLiteDatabase) {
		Sql.createTable("UPLOAD_CHECKPOINT_ENTITY") //
			.autoIncrementId() //
			.requiredInt("VAULT_ID") //
			.requiredText("TYPE") //
			.requiredText("TARGET_FOLDER_PATH") //
			.optionalText("SOURCE_FOLDER_URI") //
			.optionalText("SOURCE_FOLDER_NAME") //
			.optionalText("PENDING_FILE_URIS") //
			.requiredText("COMPLETED_FILES") //
			.requiredInt("TOTAL_FILE_COUNT") //
			.requiredInt("TIMESTAMP") //
			.requiredBool("REPLACING") //
			.executeOn(db)

		Sql.createUniqueIndex("IDX_UPLOAD_CHECKPOINT_ENTITY_VAULT_ID") //
			.on("UPLOAD_CHECKPOINT_ENTITY") //
			.asc("VAULT_ID") //
			.executeOn(db)
	}
}
