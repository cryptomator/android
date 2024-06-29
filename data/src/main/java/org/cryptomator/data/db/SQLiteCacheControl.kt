package org.cryptomator.data.db

import android.database.Cursor
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

		override fun mapCursor(cursor: Cursor): Cursor {
			return NoRequeryCursor(cursor)
		}
	}

	fun SupportSQLiteOpenHelper.Factory.asCacheControlled(): SupportSQLiteOpenHelper.Factory = asMapped(RandomUUIDMapping)
}

private class NoRequeryCursor(
	private val delegateCursor: Cursor
) : Cursor by delegateCursor {

	@Deprecated("Deprecated in Java")
	override fun requery(): Boolean {
		throw UnsupportedOperationException()
	}
}