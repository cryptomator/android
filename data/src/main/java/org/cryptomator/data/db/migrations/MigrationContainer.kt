package org.cryptomator.data.db.migrations

import androidx.sqlite.db.SupportSQLiteDatabase
import org.cryptomator.data.db.DatabaseMigration
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class MigrationContainer private constructor(private val migrations: List<DatabaseMigration>) {

	@Inject
	internal constructor(
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
		upgrade12To13: Upgrade12To13, //
		//
		migration13To14: Migration13To14, //
	) : this(
		validateMigrations(
			upgrade1To2, //
			upgrade2To3, //
			upgrade3To4, //
			upgrade4To5, //
			upgrade5To6, //
			upgrade6To7, //
			upgrade7To8, //
			upgrade8To9, //
			upgrade9To10, //
			upgrade10To11, //
			upgrade11To12, //
			upgrade12To13, //
			//
			migration13To14, //
		)
	)

	internal fun getPath(oldVersion: Int): List<DatabaseMigration> {
		return getPath(oldVersion, migrations.size + 1)
	}

	internal fun getPath(oldVersion: Int, newVersion: Int): List<DatabaseMigration> {
		require(oldVersion in 1..<newVersion && (newVersion - 1) <= migrations.size)
		return migrations.subList(oldVersion - 1, newVersion - 1)
	}

	internal fun applyPath(database: SupportSQLiteDatabase, oldVersion: Int) {
		getPath(oldVersion).forEach {
			it.migrate(database)
		}
	}

	internal fun applyPath(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
		getPath(oldVersion, newVersion).forEach {
			it.migrate(database)
		}
	}

	@JvmName("applyPathAndReturnNextTyped")
	internal inline fun <reified T : DatabaseMigration> applyPathAndReturnNext(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int): T {
		val nextMigration = migrations.getOrNull(newVersion - 1) ?: throw IllegalArgumentException("No migration from version $newVersion to ${newVersion + 1}")
		require(nextMigration is T)
		applyPath(database, oldVersion, newVersion)
		return nextMigration
	}

	internal fun applyPathAndReturnNext(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int): DatabaseMigration {
		return applyPathAndReturnNext<DatabaseMigration>(database, oldVersion, newVersion)
	}
}

private fun validateMigrations(vararg migrations: DatabaseMigration): List<DatabaseMigration> {
	require(migrations.isNotEmpty())
	return migrations.asSequence().onEachIndexed { index, migration ->
		require(migration.startVersion == (index + 1) && Math.addExact(migration.startVersion, 1) == migration.endVersion) { //
			"Illegal migration configuration"
		}
	}.toCollection(ArrayList(migrations.size))
}