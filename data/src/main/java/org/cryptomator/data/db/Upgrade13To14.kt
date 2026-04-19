package org.cryptomator.data.db

import org.cryptomator.util.FlavorConfig
import org.cryptomator.util.SharedPreferencesHandler
import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
internal class Upgrade13To14 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : DatabaseUpgrade(13, 14) {

	/**
	 * Applies the schema migration steps from version 13 to 14 on the provided database.
	 *
	 * If `origin > 0` (an existing installation), marks the welcome flow completed. For builds that
	 * require migrating a license token (non-premium flavor), reads an existing `LICENSE_TOKEN` from
	 * the database and saves it to shared preferences. In all cases, removes the `LICENSE_TOKEN`
	 * column/data from the database.
	 *
	 * @param db The database to migrate.
	 * @param origin The originating schema version; values greater than 0 indicate an existing install.
	 */
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

	/**
	 * Indicates whether the current build uses the premium flavor (i.e., does not store a license key in the database).
	 *
	 * @return `true` if the current build is the premium flavor, `false` otherwise.
	 */
	private fun nonLicenseKeyVariant(): Boolean {
		return FlavorConfig.isPremiumFlavor
	}

	/**
	 * Removes the `LICENSE_TOKEN` column from the `UPDATE_CHECK_ENTITY` table by recreating the table without that column.
	 *
	 * The existing rows for `_id`, `RELEASE_NOTE`, `VERSION`, `URL_TO_APK`, `APK_SHA256`, and `URL_TO_RELEASE_NOTE` are preserved
	 * by copying them into the new table. The operation is executed within a database transaction.
	 *
	 * @param db The database to modify.
	 */
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

	/**
	 * Retrieves the existing license token from the UPDATE_CHECK_ENTITY table.
	 *
	 * @param db The database to query.
	 * @return The `LICENSE_TOKEN` value from the first row if present, `null` otherwise.
	 */
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

	/**
	 * Marks the onboarding welcome flow as completed in shared preferences.
	 *
	 * Sets the flag that causes the welcome screen to be skipped on subsequent launches.
	 */
	private fun setWelcomeFlowCompleted() {
		sharedPreferencesHandler.setWelcomeFlowCompleted()
		Timber.tag("Upgrade13To14").i("Skip welcome screen")
	}

}
