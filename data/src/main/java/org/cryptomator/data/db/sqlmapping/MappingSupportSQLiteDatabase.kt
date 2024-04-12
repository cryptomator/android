package org.cryptomator.data.db.sqlmapping

import android.content.ContentValues
import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import timber.log.Timber

internal class MappingSupportSQLiteDatabase(
	private val delegate: SupportSQLiteDatabase,
	private val mappingFunction: SQLMappingFunction
) : SupportSQLiteDatabase by delegate {

	override fun execSQL(sql: String) {
		return delegate.execSQL(map(sql))
	}

	override fun execSQL(sql: String, bindArgs: Array<out Any?>) {
		return delegate.execSQL(map(sql), bindArgs)
	}

	override fun query(query: SupportSQLiteQuery): Cursor {
		return mapCursor(delegate.query(map(query)))
	}

	override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
		return mapCursor(delegate.query(map(query), cancellationSignal))
	}

	override fun query(query: String): Cursor {
		return mapCursor(delegate.query(map(query)))
	}

	override fun query(query: String, bindArgs: Array<out Any?>): Cursor {
		return mapCursor(delegate.query(map(query), bindArgs))
	}

	override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long {
		val processed = helper.insertWithOnConflict(table, null, values, conflictAlgorithm)
		val statement = MappingSupportSQLiteStatement(processed.sql)
		SimpleSQLiteQuery.bind(statement, processed.bindArgs)

		return statement.executeInsert()
	}

	override fun update(
		table: String,
		conflictAlgorithm: Int,
		values: ContentValues,
		whereClause: String?,
		whereArgs: Array<out Any?>?
	): Int {
		return delegate.update(table, conflictAlgorithm, values, mapWhereClause(whereClause), whereArgs)
	}

	override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int {
		return delegate.delete(table, mapWhereClause(whereClause), whereArgs)
	}

	override fun execPerConnectionSQL(sql: String, bindArgs: Array<out Any?>?) {
		delegate.execPerConnectionSQL(map(sql), bindArgs)
	}

	override fun compileStatement(sql: String): SupportSQLiteStatement {
		return MappingSupportSQLiteStatement(sql)
	}

	private val helper = AOP_SQLiteDatabase()

	private fun map(sql: String): String {
		return mappingFunction.map(sql)
	}

	private fun map(query: SupportSQLiteQuery): SupportSQLiteQuery {
		return MappingSupportSQLiteQuery(query)
	}

	private fun mapCursor(cursor: Cursor): Cursor {
		return mappingFunction.mapCursor(cursor)
	}

	private fun mapWhereClause(whereClause: String?): String? {
		if (whereClause != null && whereClause.isBlank()) {
			throw IllegalArgumentException()
		}
		return mappingFunction.mapWhereClause(whereClause)
	}

	private inner class MappingSupportSQLiteStatement(
		private val sql: String
	) : SupportSQLiteStatement {

		private val bindings = mutableListOf<(SupportSQLiteStatement) -> Unit>()

		override fun bindBlob(index: Int, value: ByteArray) {
			bindings.add { statement -> statement.bindBlob(index, value) }
		}

		override fun bindDouble(index: Int, value: Double) {
			bindings.add { statement -> statement.bindDouble(index, value) }
		}

		override fun bindLong(index: Int, value: Long) {
			bindings.add { statement -> statement.bindLong(index, value) }
		}

		override fun bindNull(index: Int) {
			bindings.add { statement -> statement.bindNull(index) }
		}

		override fun bindString(index: Int, value: String) {
			bindings.add { statement -> statement.bindString(index, value) }
		}

		override fun clearBindings() {
			bindings.clear()
		}

		override fun close() {
			//NO-OP
		}

		override fun execute() {
			newBoundStatement().use { it.execute() }
		}

		override fun executeInsert(): Long {
			return newBoundStatement().use { it.executeInsert() }
		}

		override fun executeUpdateDelete(): Int {
			return newBoundStatement().use { it.executeUpdateDelete() }
		}

		override fun simpleQueryForLong(): Long {
			return newBoundStatement().use { it.simpleQueryForLong() }
		}

		override fun simpleQueryForString(): String? {
			return newBoundStatement().use { it.simpleQueryForString() }
		}

		private fun newBoundStatement(): SupportSQLiteStatement {
			return delegate.compileStatement(map(sql)).also { statement ->
				for (binding: (SupportSQLiteStatement) -> Unit in bindings) {
					binding(statement)
				}
			}
		}
	}

	private inner class MappingSupportSQLiteQuery(
		private val delegateQuery: SupportSQLiteQuery
	) : SupportSQLiteQuery by delegateQuery {

		private val lock = Any()
		private var called = false

		private val _sql = map(delegateQuery.sql)
		override val sql: String
			get() = synchronized(lock) {
				if (called) {
					Timber.tag("MappingSupportSQLiteQuery").e("SQL queried twice")
				}
				called = true
				return _sql
			}
	}
}

private class MappingSupportSQLiteOpenHelper(
	private val delegate: SupportSQLiteOpenHelper,
	private val mappingFunction: SQLMappingFunction
) : SupportSQLiteOpenHelper by delegate {

	override val writableDatabase: SupportSQLiteDatabase
		get() = MappingSupportSQLiteDatabase(delegate.writableDatabase, mappingFunction)

	override val readableDatabase: SupportSQLiteDatabase
		get() = MappingSupportSQLiteDatabase(delegate.readableDatabase, mappingFunction)
}

private class MappingSupportSQLiteOpenHelperFactory(
	private val delegate: SupportSQLiteOpenHelper.Factory,
	private val mappingFunction: SQLMappingFunction
) : SupportSQLiteOpenHelper.Factory {

	override fun create(
		configuration: SupportSQLiteOpenHelper.Configuration
	): SupportSQLiteOpenHelper {
		return MappingSupportSQLiteOpenHelper(delegate.create(configuration), mappingFunction)
	}
}

fun SupportSQLiteOpenHelper.Factory.asMapped(mappingFunction: SQLMappingFunction): SupportSQLiteOpenHelper.Factory {
	return MappingSupportSQLiteOpenHelperFactory(this, mappingFunction)
}

interface SQLMappingFunction {

	fun map(sql: String): String

	fun mapWhereClause(whereClause: String?): String?

	fun mapCursor(cursor: Cursor): Cursor

}