package org.cryptomator.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import org.cryptomator.data.db.migrations.Migration12To13
import javax.inject.Singleton
import dagger.Module
import dagger.Provides

@Module
class DatabaseModule {

	@Singleton
	@Provides
	fun provideCryptomatorDatabase(context: Context, migrations: Array<Migration>): CryptomatorDatabase {
		return Room.databaseBuilder(context, CryptomatorDatabase::class.java, "Cryptomator") //
			.addMigrations(*migrations) //
			.addMigrations(Migration12To13) //
			.build()
	}

	@Singleton
	@Provides
	fun provideCloudDao(database: CryptomatorDatabase): CloudDao {
		return database.cloudDao()
	}

	@Singleton
	@Provides
	fun provideUpdateCheckDao(database: CryptomatorDatabase): UpdateCheckDao {
		return database.updateCheckDao()
	}

	@Singleton
	@Provides
	fun provideVaultDao(database: CryptomatorDatabase): VaultDao {
		return database.vaultDao()
	}

	@Singleton
	@Provides
	internal fun provideMigrations(
		upgrade0To1: Upgrade0To1, //
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
	): Array<Migration> = arrayOf(
		upgrade0To1,
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
	)
}