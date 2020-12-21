package org.cryptomator.data.db

import android.content.Context
import android.content.SharedPreferences
import org.cryptomator.data.db.entities.CloudEntityDao
import org.cryptomator.util.crypto.CredentialCryptor
import org.greenrobot.greendao.database.Database
import org.greenrobot.greendao.internal.DaoConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade2To3 @Inject constructor(private val context: Context) : DatabaseUpgrade(2, 3) {

	override fun internalApplyTo(db: Database, origin: Int) {
		val clouds = CloudEntityDao(DaoConfig(db, CloudEntityDao::class.java)).loadAll()
		db.beginTransaction()
		try {
			clouds.filter { cloud -> cloud.type == "DROPBOX" || cloud.type == "ONEDRIVE" } //
					.map {
						Sql.update("CLOUD_ENTITY") //
								.where("TYPE", Sql.eq(it.type)) //
								.set("ACCESS_TOKEN", Sql.toString(encrypt(if (it.type == "DROPBOX") it.accessToken else onedriveToken()))) //
								.executeOn(db)
					}
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
