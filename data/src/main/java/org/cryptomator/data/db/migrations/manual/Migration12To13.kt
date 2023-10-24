package org.cryptomator.data.db.migrations.manual

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Migration12To13 @Inject constructor() : DatabaseMigration(12, 13) {

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		//NO-OP
	}
}