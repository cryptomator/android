package org.cryptomator.data.db.migrations.manual

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class Migration12To13 @Inject constructor() : DatabaseMigration(12, 13) {

	//After migrating from v12 to v13 the database differs as follows:
	//1a) The `room_master_table` exists and contains id data

	//A v13 database created by room differs from an migrated v13 database as follows:
	//1b) The `room_master_table` exists and contains id data
	//2) The foreign key "VAULT_ENTITY.FOLDER_CLOUD_ID -> CLOUD_ENTITY._id" is unnamed
	//3) The index "IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID" is formatted differently internally
	//4) The database does not contain actual data
	//-- This does never happen in practice as databases are created by upgrading, but illustrates the slightly different schemas.
	//1a,b and 4 are intended behavior, but 2 and 3 cause the schema and the actual database to slightly drift out of sync.
	//This *should* not cause any problems.
	//The migration to v14 then recreates the tables and therefore resolves 2 and 3 as a side effect. Once the migration to
	//v14 has finished, the schema and the database are back in sync.

	//Since this is a bit hacky, "UpgradeDatabaseTest" contains the "migrate12To14IndexSideEffects"
	//and "migrate12To14ForeignKeySideEffects" methods to bring attention to any potential future changes
	//that change this behavior.

	override fun migrateInternal(db: SupportSQLiteDatabase) {
		//NO-OP
	}
}