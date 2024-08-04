package org.cryptomator.data.db.sqlmapping

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteProgram
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import org.jetbrains.annotations.VisibleForTesting

@VisibleForTesting
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
		if (values.compatIsEmpty()) {
			throw SQLiteException("Can't insert empty set of values")
		}
		if (!helper.isValidConflictAlgorithm(conflictAlgorithm)) {
			throw SQLiteException("Invalid conflict algorithm")
		}
		val processed = helper.insertWithOnConflict(table, null, values, conflictAlgorithm)
		val statement = delegate.compileStatement(map(processed.sql))
		SimpleSQLiteQuery.bind(statement, processed.bindArgs)

		return statement.use { it.executeInsert() }
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
		if (!isOpen) {
			throw SQLiteException("Database already closed")
		}
		return MappingSupportSQLiteStatement(sql)
	}

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


	@VisibleForTesting
	internal inner class MappingSupportSQLiteStatement(
		private val sql: String
	) : SupportSQLiteStatement {

		private val bindings = mutableListOf<Any?>()

		private fun saveBinding(index: Int, value: Any?): Any? = synchronized(bindings) {
			return@synchronized bindings.setLeniently(index - 1, value)
		}

		override fun bindBlob(index: Int, value: ByteArray) {
			saveBinding(index, value.copyOf())
		}

		override fun bindDouble(index: Int, value: Double) {
			saveBinding(index, value)
		}

		override fun bindLong(index: Int, value: Long) {
			saveBinding(index, value)
		}

		override fun bindNull(index: Int) {
			saveBinding(index, null)
		}

		override fun bindString(index: Int, value: String) {
			saveBinding(index, value)
		}

		override fun clearBindings(): Unit = synchronized(bindings) {
			return@synchronized bindings.clear()
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

		@VisibleForTesting
		internal fun newBoundStatement(): SupportSQLiteStatement {
			return delegate.compileStatement(map(sql)).also { statement ->
				SimpleSQLiteQuery.bind(statement, prepareBindArgs())
			}
		}

		private fun prepareBindArgs(): Array<Any?> = synchronized(bindings) {
			return@synchronized bindings.asSequence().map { binding -> prepareSingleBindArg(binding) }.toArray(bindings.size)
		}

		private fun prepareSingleBindArg(binding: Any?): Any? {
			return when (binding) {
				is ByteArray -> binding.copyOf()
				else -> binding
			}
		}
	}

	@VisibleForTesting
	internal inner class MappingSupportSQLiteQuery(
		private val delegateQuery: SupportSQLiteQuery
	) : SupportSQLiteQuery by delegateQuery {

		private val _sql = map(delegateQuery.sql)
		private val sqlDelegate = OneOffDelegate { throw IllegalStateException("SQL queried twice") }
		private val bindToDelegate = OneOffDelegate { throw IllegalStateException("bindTo called twice") }

		override val sql: String
			get() = sqlDelegate.call { _sql }

		override fun bindTo(statement: SupportSQLiteProgram) {
			bindToDelegate.call { delegateQuery.bindTo(statement) }
		}
	}
}

private val helper = AOP_SQLiteDatabase()

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

/**
 * Implementations must be threadsafe.
 */
interface SQLMappingFunction {

	fun map(sql: String): String

	fun mapWhereClause(whereClause: String?): String?

	fun mapCursor(cursor: Cursor): Cursor

}