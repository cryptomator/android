package org.cryptomator.data.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

//TODO Verify if Metadata from earlier tables was lost
object Migration12To13 : Migration(12, 13) {

	override fun migrate(database: SupportSQLiteDatabase) {
		//NO-OP
	}
}