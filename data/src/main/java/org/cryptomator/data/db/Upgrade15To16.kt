package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.Sql.SqlCreateTableBuilder.ForeignKeyBehaviour
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade15To16 @Inject constructor() : DatabaseUpgrade(15, 16) {

	override fun internalMigrate(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			createDocumentMetadataEntity(db)
			createCachedFileEntity(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun createDocumentMetadataEntity(db: SupportSQLiteDatabase) {
		Sql.createTable("DOCUMENT_METADATA_ENTITY") //
			.autoIncrementId() //
			.requiredInt("VAULT_ID") //
			.requiredText("PATH") //
			.optionalText("PARENT_PATH") //
			.requiredText("NAME") //
			.requiredText("MIME_TYPE") //
			.optionalInt("SIZE") //
			.optionalInt("LAST_MODIFIED") //
			.optionalInt("LAST_ENUMERATED_AT") //
			.requiredText("STATUS") //
			.optionalText("SOURCE_PATH") //
			.requiredInt("UPLOAD_GENERATION") //
			.foreignKey("VAULT_ID", "VAULT_ENTITY", ForeignKeyBehaviour.ON_DELETE_CASCADE) //
			.executeOn(db)

		Sql.createUniqueIndex("IDX_DOCUMENT_METADATA_ENTITY_VAULT_ID_PATH") //
			.on("DOCUMENT_METADATA_ENTITY") //
			.asc("VAULT_ID") //
			.asc("PATH") //
			.executeOn(db)

		Sql.createIndex("IDX_DOCUMENT_METADATA_ENTITY_VAULT_ID_PARENT_PATH") //
			.on("DOCUMENT_METADATA_ENTITY") //
			.asc("VAULT_ID") //
			.asc("PARENT_PATH") //
			.executeOn(db)
	}

	private fun createCachedFileEntity(db: SupportSQLiteDatabase) {
		Sql.createTable("CACHED_FILE_ENTITY") //
			.requiredInt("VAULT_ID") //
			.requiredText("PATH") //
			.requiredText("LOCAL_NAME") //
			.optionalInt("LAST_MODIFIED") //
			.primaryKey("VAULT_ID", "PATH") //
			.foreignKey(arrayOf("VAULT_ID", "PATH"), "DOCUMENT_METADATA_ENTITY", arrayOf("VAULT_ID", "PATH"), ForeignKeyBehaviour.ON_UPDATE_CASCADE, ForeignKeyBehaviour.ON_DELETE_CASCADE) //
			.executeOn(db)
	}
}
