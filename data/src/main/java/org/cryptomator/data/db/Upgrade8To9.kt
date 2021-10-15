package org.cryptomator.data.db

import org.cryptomator.util.SharedPreferencesHandler
import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade8To9 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : DatabaseUpgrade(8, 9) {

	override fun internalApplyTo(db: Database, origin: Int) {
		// toggle beta screen dialog already shown to display it again in this beta
		sharedPreferencesHandler.setBetaScreenDialogAlreadyShown(false)
	}
}
