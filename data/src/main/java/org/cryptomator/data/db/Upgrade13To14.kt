package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
internal class Upgrade13To14 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : DatabaseUpgrade(13, 14) {

	override fun internalMigrate(db: SupportSQLiteDatabase) {
		// Only an existing database is ever migrated, so this is an existing user — skip welcome
		setWelcomeFlowCompleted()
		if (!nonLicenseKeyVariant()) {
			val licenseToken = getExistingLicenseToken(db)
			if (licenseToken != null) {
				sharedPreferencesHandler.setLicenseToken(licenseToken)
			}
		}
		removeLicenseFromDb(db)
	}

	private fun nonLicenseKeyVariant(): Boolean {
		return FlavorConfig.isPremiumFlavor
	}

	private fun removeLicenseFromDb(db: SupportSQLiteDatabase) {
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

	private fun getExistingLicenseToken(db: SupportSQLiteDatabase): String? {
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
