package org.cryptomator.data.db

import android.database.Cursor
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.entities.CloudEntity
import org.cryptomator.data.db.entities.UpdateCheckEntity
import org.cryptomator.data.db.entities.VaultEntity
import org.cryptomator.data.db.migrations.Sql
import org.cryptomator.data.db.migrations.auto.AutoMigration14To15
import kotlin.math.max

const val DATABASE_NAME = "Cryptomator"
const val CRYPTOMATOR_DATABASE_VERSION = 15

@Database(
	version = CRYPTOMATOR_DATABASE_VERSION, entities = [CloudEntity::class, UpdateCheckEntity::class, VaultEntity::class], autoMigrations = [
		AutoMigration(from = 14, to = 15, spec = AutoMigration14To15::class)
	]
)
abstract class CryptomatorDatabase : RoomDatabase() {

	abstract fun cloudDao(): CloudDao

	abstract fun updateCheckDao(): UpdateCheckDao

	abstract fun vaultDao(): VaultDao
}

val SupportSQLiteDatabase.foreignKeyConstraintsEnabled: Boolean
	get() {
		query("PRAGMA foreign_keys;").use { cursor ->
			check(cursor.count == 1 && cursor.moveToNext()) { "\"PRAGMA foreign_keys\" returned invalid value" }
			return cursor.getLong(0) == 1L
		}
	}
val SupportSQLiteDatabase.hasRoomMasterTable: Boolean
	get() {
		return Sql.query("sqlite_master") //
			.columns(listOf("count(*)")) //
			.where("name", Sql.eq("room_master_table")) //
			.executeOn(this) //
			.use { cursor ->
				cursor.moveToNext() && cursor.getInt(0) == 1
			}
	}

/**
 * @param type The type of the value; any of [Cursor.FIELD_TYPE_NULL], [Cursor.FIELD_TYPE_INTEGER], [Cursor.FIELD_TYPE_FLOAT], [Cursor.FIELD_TYPE_STRING] or [Cursor.FIELD_TYPE_BLOB]
 * @param value The value matching the type; `null` for [Cursor.FIELD_TYPE_NULL]
 */
data class CursorValue(val type: Int, val value: Any?) {

	init {
		require((type == Cursor.FIELD_TYPE_NULL) == (value == null))
	}
}

fun Cursor.getValue(columnIndex: Int): CursorValue {
	require(0 <= columnIndex && columnIndex < this.columnCount) { "Column index $columnIndex outside range 0 <= index < ${this.columnCount}." }
	return when (getType(columnIndex)) {
		Cursor.FIELD_TYPE_INTEGER -> CursorValue(Cursor.FIELD_TYPE_INTEGER, getLong(columnIndex))
		Cursor.FIELD_TYPE_FLOAT -> CursorValue(Cursor.FIELD_TYPE_FLOAT, getDouble(columnIndex))
		Cursor.FIELD_TYPE_STRING -> CursorValue(Cursor.FIELD_TYPE_STRING, getString(columnIndex))
		Cursor.FIELD_TYPE_BLOB -> CursorValue(Cursor.FIELD_TYPE_BLOB, getBlob(columnIndex))
		Cursor.FIELD_TYPE_NULL -> CursorValue(Cursor.FIELD_TYPE_NULL, null)
		else -> throw IllegalArgumentException()
	}
}

fun Cursor.getValueAsString(columnIndex: Int): String {
	require(0 <= columnIndex && columnIndex < this.columnCount) { "Column index $columnIndex outside range 0 <= index < ${this.columnCount}." }
	return when (getType(columnIndex)) {
		Cursor.FIELD_TYPE_INTEGER -> getLong(columnIndex).toString()
		Cursor.FIELD_TYPE_FLOAT -> getDouble(columnIndex).toBigDecimal().toPlainString()
		Cursor.FIELD_TYPE_STRING -> "\"${getString(columnIndex)}\""
		Cursor.FIELD_TYPE_BLOB -> getBlob(columnIndex).asSequence().map {
			it.toUByte().toString(16)
		}.joinToString(separator = " ", prefix = "0x")
		Cursor.FIELD_TYPE_NULL -> "null"
		else -> throw IllegalArgumentException()
	}
}

fun Cursor.equalsCursor(other: Cursor): Boolean {
	if (this.count != other.count) {
		return false
	}
	if (this.columnCount != other.columnCount) {
		return false
	}
	if (this.columnNames.uniqueToSet() != other.columnNames.uniqueToSet()) {
		return false
	}

	val thisPos = this.position
	this.moveToPosition(-1)

	val otherPos = other.position
	other.moveToPosition(-1)

	while (this.moveToNext()) {
		require(other.moveToNext())
		for (name in this.columnNames) {
			val valueThis = this.getValue(this.getColumnIndexOrThrow(name))
			val valueOther = other.getValue(other.getColumnIndexOrThrow(name))

			if (valueThis != valueOther) {
				this.moveToPosition(thisPos)
				other.moveToPosition(otherPos)
				return false
			}
		}
	}
	this.moveToPosition(thisPos)
	other.moveToPosition(otherPos)
	return true
}

fun Cursor?.stringify(): String {
	if (this == null) {
		return "<null>"
	}
	if (this.columnCount == 0) {
		return "<empty>"
	}
	if (this.count == 0) {
		return this.columnNames.joinToString(" ")
	}
	val startPos = this.position
	this.moveToPosition(-1)

	val columnWidths: MutableMap<String, Int> = this.columnNames.associateWithTo(mutableMapOf()) { it.length }
	val values = buildList(this.count * this.columnCount) {
		while (moveToNext()) {
			for (name in columnNames) {
				val value = getValueAsString(getColumnIndexOrThrow(name))
				columnWidths.compute(name) { _, currentWidth: Int? -> max(currentWidth!!, value.length) }
				add(value)
			}
		}
	}

	this.moveToPosition(startPos)
	val stringifiedRowCount = this.count + 1 /* Header */
	val rowCapacity = columnWidths.values.sum() + this.columnCount /* V-Spaces */ // - 1 (V-Spaces) + 1 (Line breaks)
	val capacity = stringifiedRowCount * rowCapacity // + 1 (H-Space) - 1 (No Line break in last line)
	return buildString(capacity) {
		appendLine(columnNames.asSequence().map { it.padEnd(columnWidths[it]!!) }.joinToString(" "))
		appendLine()
		values.forEachIndexed { i: Int, value: String ->
			append(value.padEnd(columnWidths[columnNames[i % columnCount]]!!))
			if ((i == values.size - 1)) {
				//Last element
				return@buildString
			}
			if ((i + 1) % columnCount == 0) {
				//Last element in line
				appendLine()
			} else {
				append(" ")
			}
		}
	}
}

private fun <T> Array<T>.uniqueToSet(): Set<T> = toSet().also {
	require(this.size == it.size) { "Array contained ${this.size - it.size} duplicate elements." }
}

fun SupportSQLiteDatabase.applyDefaultConfiguration(assertedWalEnabledStatus: Boolean) {
	require(isWriteAheadLoggingEnabled == assertedWalEnabledStatus) {
		"Expected WAL enabled status to be $assertedWalEnabledStatus for \"${path}\", but was $isWriteAheadLoggingEnabled"
	}
	setForeignKeyConstraintsEnabled(true)
}