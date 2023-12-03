package org.cryptomator.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import org.cryptomator.data.db.entities.CloudEntity
import org.cryptomator.data.db.entities.UpdateCheckEntity
import org.cryptomator.data.db.entities.VaultEntity
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