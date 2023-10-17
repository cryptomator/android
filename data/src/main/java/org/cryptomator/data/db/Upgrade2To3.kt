package org.cryptomator.data.db

import android.content.Context
import android.content.SharedPreferences
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.util.crypto.CredentialCryptor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade2To3 @Inject constructor(private val context: Context) : Migration(2, 3) {

	override fun migrate(db: SupportSQLiteDatabase) {
		db.beginTransaction()
		try {
			Sql.query("CLOUD_ENTITY")
				.columns(listOf("ACCESS_TOKEN"))
				.where("TYPE", Sql.eq("DROPBOX"))
				.executeOn(db).use {
					if (it.moveToFirst()) {
						Sql.update("CLOUD_ENTITY")
							.set("ACCESS_TOKEN", Sql.toString(encrypt(it.getString(it.getColumnIndex("ACCESS_TOKEN")))))
							.where("TYPE", Sql.eq("DROPBOX"))
							.executeOn(db)
					}
				}

			Sql.update("CLOUD_ENTITY")
				.set("ACCESS_TOKEN", Sql.toString(encrypt(onedriveToken())))
				.where("TYPE", Sql.eq("ONEDRIVE"))
				.executeOn(db)

			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun encrypt(token: String?): String? {
		return if (token == null) null else CredentialCryptor //
			.getInstance(context) //
			.encrypt(token)
	}

	private fun onedriveToken(): String? {
		val prefKey = "refresh_token"
		val settings: SharedPreferences = context.getSharedPreferences("com.microsoft.live", Context.MODE_PRIVATE)
		val value = settings.getString(prefKey, null)
		settings.edit().remove(prefKey).commit()
		return value
	}
}
