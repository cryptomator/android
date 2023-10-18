package org.cryptomator.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import org.cryptomator.data.db.entities.CloudEntity
import org.cryptomator.data.db.entities.UpdateCheckEntity
import org.cryptomator.data.db.entities.VaultEntity
import org.cryptomator.data.db.migrations.Migration13To14

@Database(
	version = 14, entities = [CloudEntity::class, UpdateCheckEntity::class, VaultEntity::class], autoMigrations = [
		AutoMigration(from = 13, to = 14, spec = Migration13To14::class)
	]
)
abstract class CryptomatorDatabase : RoomDatabase() {

	abstract fun cloudDao(): CloudDao

	abstract fun updateCheckDao(): UpdateCheckDao

	abstract fun vaultDao(): VaultDao
}