package org.cryptomator.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import org.cryptomator.data.db.entities.CloudEntity
import org.cryptomator.data.db.entities.UpdateCheckEntity
import org.cryptomator.data.db.entities.VaultEntity

const val DATABASE_NAME = "Cryptomator"
const val CRYPTOMATOR_DATABASE_VERSION = 15

@Database(
	version = CRYPTOMATOR_DATABASE_VERSION, entities = [CloudEntity::class, UpdateCheckEntity::class, VaultEntity::class], autoMigrations = [

	]
)
abstract class CryptomatorDatabase : RoomDatabase() {

}