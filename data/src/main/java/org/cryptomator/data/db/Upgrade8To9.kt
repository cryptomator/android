package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade8To9 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : DatabaseUpgrade(8, 9) {

	override fun internalMigrate(db: SupportSQLiteDatabase) {
		// toggle beta screen dialog already shown to display it again in this beta
		sharedPreferencesHandler.setBetaScreenDialogAlreadyShown(false)
	}
}
