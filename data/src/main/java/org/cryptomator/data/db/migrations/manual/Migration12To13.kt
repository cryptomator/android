package org.cryptomator.data.db.migrations.manual

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration

//TODO Verify if Metadata from earlier tables was lost
object Migration12To13 : DatabaseMigration(12, 13) {

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		//NO-OP
	}
}