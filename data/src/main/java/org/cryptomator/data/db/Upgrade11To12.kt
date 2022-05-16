package org.cryptomator.data.db

import com.google.common.base.Optional
import org.cryptomator.util.SharedPreferencesHandler
import org.greenrobot.greendao.database.Database
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade11To12 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : DatabaseUpgrade(11, 12) {

	override fun internalApplyTo(db: Database, origin: Int) {
		when (sharedPreferencesHandler.updateIntervalInDays()) {
			Optional.of(7), Optional.of(30) -> sharedPreferencesHandler.setUpdateIntervalInDays(Optional.of(1))
		}
	}
}
