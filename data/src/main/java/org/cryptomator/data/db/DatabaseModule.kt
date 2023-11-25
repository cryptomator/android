package org.cryptomator.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.migrations.legacy.Upgrade10To11
import org.cryptomator.data.db.migrations.legacy.Upgrade11To12
import org.cryptomator.data.db.migrations.legacy.Upgrade1To2
import org.cryptomator.data.db.migrations.legacy.Upgrade2To3
import org.cryptomator.data.db.migrations.legacy.Upgrade3To4
import org.cryptomator.data.db.migrations.legacy.Upgrade4To5
import org.cryptomator.data.db.migrations.legacy.Upgrade5To6
import org.cryptomator.data.db.migrations.legacy.Upgrade6To7
import org.cryptomator.data.db.migrations.legacy.Upgrade7To8
import org.cryptomator.data.db.migrations.legacy.Upgrade8To9
import org.cryptomator.data.db.migrations.legacy.Upgrade9To10
import org.cryptomator.data.db.migrations.manual.Migration12To13
import javax.inject.Provider
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import timber.log.Timber

@Module
class DatabaseModule {

	@Singleton
	@Provides
	fun provideCryptomatorDatabase(context: Context, migrations: Array<Migration>): CryptomatorDatabase {
		Timber.tag("Database").i("Building database (target version: %s)", CRYPTOMATOR_DATABASE_VERSION)
		return Room.databaseBuilder(context, CryptomatorDatabase::class.java, "Cryptomator") //
			.createFromAsset(BASE_DATABASE_ASSET) //
			.addMigrations(*migrations) //
			.addCallback(DatabaseCallback) //
			.build() //Fails if no migration is found (especially when downgrading)
			.also { //
				//Migrations are only triggered once the database is used for the first time.
				//-- Let's do that now and verify all went well before returning the database.
				require(it.openHelper.writableDatabase.version == CRYPTOMATOR_DATABASE_VERSION)
				Timber.tag("Database").i("Database built successfully")
			}
	}

	@Singleton
	@Provides
	fun provideCloudDao(database: Provider<CryptomatorDatabase>): CloudDao {
		return database.get().cloudDao()
	}

	@Singleton
	@Provides
	fun provideUpdateCheckDao(database: Provider<CryptomatorDatabase>): UpdateCheckDao {
		return database.get().updateCheckDao()
	}

	@Singleton
	@Provides
	fun provideVaultDao(database: Provider<CryptomatorDatabase>): VaultDao {
		return database.get().vaultDao()
	}

	@Singleton
	@Provides
	internal fun provideMigrations(
		upgrade1To2: Upgrade1To2, //
		upgrade2To3: Upgrade2To3, //
		upgrade3To4: Upgrade3To4, //
		upgrade4To5: Upgrade4To5, //
		upgrade5To6: Upgrade5To6, //
		upgrade6To7: Upgrade6To7, //
		upgrade7To8: Upgrade7To8, //
		upgrade8To9: Upgrade8To9, //
		upgrade9To10: Upgrade9To10, //
		upgrade10To11: Upgrade10To11, //
		upgrade11To12: Upgrade11To12, //
		//
		migration12To13: Migration12To13, //
	): Array<Migration> = arrayOf(
		upgrade1To2,
		upgrade2To3,
		upgrade5To6,
		upgrade3To4,
		upgrade4To5,
		upgrade6To7,
		upgrade7To8,
		upgrade8To9,
		upgrade9To10,
		upgrade10To11,
		upgrade11To12,
		//
		migration12To13,
	)

	companion object {

		const val BASE_DATABASE_ASSET = "databases/legacy/Cryptomator_DB_v1.db"
	}
}

object DatabaseCallback : RoomDatabase.Callback() {

	override fun onCreate(db: SupportSQLiteDatabase) {
		Timber.tag("Database").i("Created database (v%s)", db.version)
	}

	override fun onOpen(db: SupportSQLiteDatabase) {
		Timber.tag("Database").i("Opened database (v%s)", db.version)
	}

	override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
		//This should not be called
		throw UnsupportedOperationException("Destructive migration is not supported")
	}
}