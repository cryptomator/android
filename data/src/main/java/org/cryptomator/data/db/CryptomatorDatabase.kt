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
import org.cryptomator.data.db.migrations.auto.AutoMigration13To14

const val DATABASE_NAME = "Cryptomator"
const val CRYPTOMATOR_DATABASE_VERSION = 14

@Database(
	version = CRYPTOMATOR_DATABASE_VERSION, entities = [CloudEntity::class, UpdateCheckEntity::class, VaultEntity::class], autoMigrations = [
		AutoMigration(from = 13, to = 14, spec = AutoMigration13To14::class)
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

private fun <T> Array<T>.uniqueToSet(): Set<T> = toSet().also {
	require(this.size == it.size) { "Array contained ${this.size - it.size} duplicate elements." }
}