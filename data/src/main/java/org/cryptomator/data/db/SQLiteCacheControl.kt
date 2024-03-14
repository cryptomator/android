package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.cryptomator.data.db.sqlmapping.SQLMappingFunction
import org.cryptomator.data.db.sqlmapping.asMapped
import java.util.UUID

object SQLiteCacheControl {

	object RandomUUIDMapping : SQLMappingFunction {

		private val newIdentifier: String
			get() = UUID.randomUUID().toString()

		override fun invoke(sql: String): String {
			return "$sql -- $newIdentifier"
		}
	}

	fun SupportSQLiteOpenHelper.Factory.asCacheControlled(): SupportSQLiteOpenHelper.Factory = asMapped(RandomUUIDMapping)
}