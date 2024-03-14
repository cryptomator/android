package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.cryptomator.data.db.sqlmapping.SQLMappingFunction
import org.cryptomator.data.db.sqlmapping.asMapped
import java.util.UUID

object SQLiteCacheControl {

	object RandomUUIDMapping : SQLMappingFunction {

		private val newIdentifier: String
			get() = UUID.randomUUID().toString()

		override fun map(sql: String): String {
			return "$sql -- $newIdentifier"
		}

		override fun mapWhereClause(whereClause: String?): String {
			return map(whereClause ?: "1 = 1")
		}
	}

	fun SupportSQLiteOpenHelper.Factory.asCacheControlled(): SupportSQLiteOpenHelper.Factory = asMapped(RandomUUIDMapping)
}