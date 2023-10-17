package org.cryptomator.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.base.Optional
import org.cryptomator.util.SharedPreferencesHandler
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Upgrade11To12 @Inject constructor(private val sharedPreferencesHandler: SharedPreferencesHandler) : Migration(11, 12) {

	override fun migrate(db: SupportSQLiteDatabase) {
		when (sharedPreferencesHandler.updateIntervalInDays()) {
			Optional.of(7), Optional.of(30) -> sharedPreferencesHandler.setUpdateIntervalInDays(Optional.of(1))
		}
	}
}
