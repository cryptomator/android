package org.cryptomator.data.db

import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
internal class Upgrade13To14 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : DatabaseUpgrade(13, 14) {

	override fun internalApplyTo(db: Database, origin: Int) {
		if (origin > 0) {
			// Any user going through a schema migration is an existing user — skip welcome
			setWelcomeFlowCompleted()
			if (!nonLicenseKeyVariant()) {
				val licenseToken = getExistingLicenseToken(db)
				if (licenseToken != null) {
					sharedPreferencesHandler.setLicenseToken(licenseToken)
				}
			}
		}
		removeLicenseFromDb(db)
	}

	private fun nonLicenseKeyVariant(): Boolean {
		return FlavorConfig.isPremiumFlavor
	}

	private fun removeLicenseFromDb(db: Database) {
		db.beginTransaction()
		try {
			Sql.alterTable("UPDATE_CHECK_ENTITY").renameTo("UPDATE_CHECK_ENTITY_OLD").executeOn(db)

			Sql.createTable("UPDATE_CHECK_ENTITY") //
				.id() //
				.optionalText("RELEASE_NOTE") //
				.optionalText("VERSION") //
				.optionalText("URL_TO_APK") //
				.optionalText("APK_SHA256") //
				.optionalText("URL_TO_RELEASE_NOTE") //
				.executeOn(db)

			Sql.insertInto("UPDATE_CHECK_ENTITY") //
				.select("_id", "RELEASE_NOTE", "VERSION", "URL_TO_APK", "APK_SHA256", "URL_TO_RELEASE_NOTE") //
				.columns("_id", "RELEASE_NOTE", "VERSION", "URL_TO_APK", "APK_SHA256", "URL_TO_RELEASE_NOTE") //
				.from("UPDATE_CHECK_ENTITY_OLD") //
				.executeOn(db)

			Sql.dropTable("UPDATE_CHECK_ENTITY_OLD").executeOn(db)
			db.setTransactionSuccessful()
		} finally {
			db.endTransaction()
		}
	}

	private fun getExistingLicenseToken(db: Database): String? {
		Sql.query("UPDATE_CHECK_ENTITY")
			.columns(listOf("LICENSE_TOKEN"))
			.executeOn(db).use {
				if (it.moveToNext()) {
					return it.getString(it.getColumnIndex("LICENSE_TOKEN"))
				}
			}
		return null
	}

	private fun setWelcomeFlowCompleted() {
		sharedPreferencesHandler.setWelcomeFlowCompleted()
		Timber.tag("Upgrade13To14").i("Skip welcome screen")
	}

}
