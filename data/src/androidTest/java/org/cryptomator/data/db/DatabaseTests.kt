package org.cryptomator.data.db

import android.content.Context
import androidx.room.util.readVersion
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.cryptomator.data.db.migrations.MigrationContainer
import org.cryptomator.data.db.migrations.legacy.Upgrade10To11
import org.cryptomator.data.db.migrations.legacy.Upgrade11To12
import org.cryptomator.data.db.migrations.legacy.Upgrade12To13
import org.cryptomator.data.db.migrations.legacy.Upgrade1To2
import org.cryptomator.data.db.migrations.legacy.Upgrade2To3
import org.cryptomator.data.db.migrations.legacy.Upgrade3To4
import org.cryptomator.data.db.migrations.legacy.Upgrade4To5
import org.cryptomator.data.db.migrations.legacy.Upgrade5To6
import org.cryptomator.data.db.migrations.legacy.Upgrade6To7
import org.cryptomator.data.db.migrations.legacy.Upgrade7To8
import org.cryptomator.data.db.migrations.legacy.Upgrade8To9
import org.cryptomator.data.db.migrations.legacy.Upgrade9To10
import org.cryptomator.data.db.migrations.manual.Migration13To14
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.fail

private const val LATEST_LEGACY_MIGRATION = 13

internal fun configureTestDatabase(context: Context, databaseName: String): SupportSQLiteOpenHelper.Configuration {
	return SupportSQLiteOpenHelper.Configuration.builder(context) //
		.name(databaseName) //
		.callback(object : SupportSQLiteOpenHelper.Callback(LATEST_LEGACY_MIGRATION) {
			override fun onConfigure(db: SupportSQLiteDatabase) = db.applyDefaultConfiguration( //
				assertedWalEnabledStatus = false //
			)

			override fun onCreate(db: SupportSQLiteDatabase) {
				fail("Database should not be created, but copied from template")
			}

			override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
				assertEquals(1, oldVersion)
				assertEquals(LATEST_LEGACY_MIGRATION, newVersion)
			}
		}).build()
}

internal fun createVersion0Database(context: Context, databaseName: String) {
	val config = SupportSQLiteOpenHelper.Configuration.builder(context) //
		.name(databaseName) //
		.callback(object : SupportSQLiteOpenHelper.Callback(1) {
			override fun onConfigure(db: SupportSQLiteDatabase) = db.applyDefaultConfiguration( //
				assertedWalEnabledStatus = false //
			)

			override fun onCreate(db: SupportSQLiteDatabase) = throw InterruptCreationException()
			override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = throw IllegalStateException()
		}).build()

	FrameworkSQLiteOpenHelperFactory().create(config).use { openHelper ->
		openHelper.setWriteAheadLoggingEnabled(false)
		try {
			//The "use" block in "initVersion0Database" should not be reached, let alone finished; ...
			initVersion0Database(openHelper)
		} catch (e: InterruptCreationException) {
			//... instead, the creation of the database should be interrupted by the InterruptCreationException thrown by "onCreate",
			//so that this catch block is called and the database remains in version 0.
			require(readVersion(context.getDatabasePath(databaseName)) == 0)
		}
	}
}

@Suppress("serial")
private class InterruptCreationException : Exception()

private fun initVersion0Database(openHelper: SupportSQLiteOpenHelper): Nothing {
	openHelper.writableDatabase.use {
		throw IllegalStateException("Creating a v0 database requires throwing an exception during creation (got ${it.version})")
	}
}

internal fun createMigrationContainer(
	context: Context, //
	sharedPreferencesHandler: SharedPreferencesHandler
) = MigrationContainer(
	Upgrade1To2(), //
	Upgrade2To3(context), //
	Upgrade3To4(), //
	Upgrade4To5(), //
	Upgrade5To6(), //
	Upgrade6To7(), //
	Upgrade7To8(), //
	Upgrade8To9(sharedPreferencesHandler), //
	Upgrade9To10(sharedPreferencesHandler), //
	Upgrade10To11(), //
	Upgrade11To12(sharedPreferencesHandler), //
	Upgrade12To13(context), //
	//
	Migration13To14(), //
	//Auto: 14 -> 15
)