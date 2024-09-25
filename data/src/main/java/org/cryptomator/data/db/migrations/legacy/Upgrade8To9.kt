package org.cryptomator.data.db.migrations.legacy

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade8To9 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : DatabaseMigration(8, 9) {

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		// toggle beta screen dialog already shown to display it again in this beta
		sharedPreferencesHandler.setBetaScreenDialogAlreadyShown(false)
	}
}
