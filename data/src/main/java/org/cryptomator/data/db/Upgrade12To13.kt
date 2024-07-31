package org.cryptomator.data.db

import android.content.Context
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.CryptoMode
import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade12To13 @Inject constructor(private val context: Context) : DatabaseUpgrade(12, 13) {

	override fun internalApplyTo(db: Database, origin: Int) {
		db.beginTransaction()
		try {
			addCryptoModeToDbEntities(db)
			addDefaultPasswordCryptoModeToDb(db)
			upgradeCloudCryptoModeToGCM(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun addCryptoModeToDbEntities(db: Database) {
		Sql.alterTable("CLOUD_ENTITY").renameTo("CLOUD_ENTITY_OLD").executeOn(db)

		Sql.createTable("CLOUD_ENTITY") //
			.id() //
			.requiredText("TYPE") //
			.optionalText("ACCESS_TOKEN") //
			.optionalText("ACCESS_TOKEN_CRYPTO_MODE") //
			.optionalText("URL") //
			.optionalText("USERNAME") //
			.optionalText("WEBDAV_CERTIFICATE") //
			.optionalText("S3_BUCKET") //
			.optionalText("S3_REGION") //
			.optionalText("S3_SECRET_KEY") //
			.optionalText("S3_SECRET_KEY_CRYPTO_MODE") //
			.executeOn(db)

		Sql.insertInto("CLOUD_ENTITY") //
			.select("_id", "TYPE", "ACCESS_TOKEN", "URL", "USERNAME", "WEBDAV_CERTIFICATE", "S3_BUCKET", "S3_REGION", "S3_SECRET_KEY") //
			.columns("_id", "TYPE", "ACCESS_TOKEN", "URL", "USERNAME", "WEBDAV_CERTIFICATE", "S3_BUCKET", "S3_REGION", "S3_SECRET_KEY") //
			.from("CLOUD_ENTITY_OLD") //
			.executeOn(db)

		// use this to recreate the index but also add the new column as well
		addPasswordCryptoModeToVaultDbEntity(db)

		Sql.dropTable("CLOUD_ENTITY_OLD").executeOn(db)
	}

	private fun addPasswordCryptoModeToVaultDbEntity(db: Database) {
		Sql.alterTable("VAULT_ENTITY").renameTo("VAULT_ENTITY_OLD").executeOn(db)
		Sql.createTable("VAULT_ENTITY") //
			.id() //
			.optionalInt("FOLDER_CLOUD_ID") //
			.optionalText("FOLDER_PATH") //
			.optionalText("FOLDER_NAME") //
			.optionalInt("FORMAT") //
			.requiredText("CLOUD_TYPE") //
			.optionalText("PASSWORD") //
			.optionalText("PASSWORD_CRYPTO_MODE") //
			.optionalInt("POSITION") //
			.optionalInt("SHORTENING_THRESHOLD") //
			.foreignKey("FOLDER_CLOUD_ID", "CLOUD_ENTITY", Sql.SqlCreateTableBuilder.ForeignKeyBehaviour.ON_DELETE_SET_NULL) //
			.executeOn(db)

		Sql.insertInto("VAULT_ENTITY") //
			.select("_id", "FOLDER_CLOUD_ID", "FOLDER_PATH", "FOLDER_NAME", "FORMAT", "PASSWORD", "POSITION", "SHORTENING_THRESHOLD", "CLOUD_ENTITY.TYPE") //
			.columns("_id", "FOLDER_CLOUD_ID", "FOLDER_PATH", "FOLDER_NAME", "FORMAT", "PASSWORD", "POSITION", "SHORTENING_THRESHOLD", "CLOUD_TYPE") //
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

	private fun addDefaultPasswordCryptoModeToDb(db: Database) {
		Sql.update("VAULT_ENTITY") //
			.where("PASSWORD", Sql.isNotNull())
			.set("PASSWORD_CRYPTO_MODE", Sql.toString(CryptoMode.CBC.name)) //
			.executeOn(db)
	}

	private fun upgradeCloudCryptoModeToGCM(db: Database) {
		val gcmCryptor = CredentialCryptor.getInstance(context, CryptoMode.GCM)
		val cbcCryptor = CredentialCryptor.getInstance(context, CryptoMode.CBC)

		Sql.query("CLOUD_ENTITY").where("ACCESS_TOKEN", Sql.isNotNull()).executeOn(db).use {
			while (it.moveToNext()) {
				Sql.update("CLOUD_ENTITY")
					.where("_id", Sql.eq(it.getLong(it.getColumnIndex("_id"))))
					.set("ACCESS_TOKEN", Sql.toString(reEncrypt(it.getString(it.getColumnIndex("ACCESS_TOKEN")), gcmCryptor, cbcCryptor)))
					.set("ACCESS_TOKEN_CRYPTO_MODE", Sql.toString(CryptoMode.GCM.name))
					.executeOn(db)
			}
		}
		Sql.query("CLOUD_ENTITY").where("S3_SECRET_KEY", Sql.isNotNull()).executeOn(db).use {
			while (it.moveToNext()) {
				Sql.update("CLOUD_ENTITY")
					.where("_id", Sql.eq(it.getLong(it.getColumnIndex("_id"))))
					.set("S3_SECRET_KEY", Sql.toString(reEncrypt(it.getString(it.getColumnIndex("S3_SECRET_KEY")), gcmCryptor, cbcCryptor)))
					.set("S3_SECRET_KEY_CRYPTO_MODE", Sql.toString(CryptoMode.GCM.name))
					.executeOn(db)
			}
		}
	}

	private fun reEncrypt(ciphertext: String?, gcmCryptor: CredentialCryptor, cbcCryptor: CredentialCryptor): String? {
		if (ciphertext == null) return null
		val accessToken = cbcCryptor.decrypt(ciphertext)
		return gcmCryptor.encrypt(accessToken)
	}
}
