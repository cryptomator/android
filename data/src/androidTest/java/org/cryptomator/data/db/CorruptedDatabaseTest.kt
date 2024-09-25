package org.cryptomator.data.db

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.util.readVersion
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
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
import org.cryptomator.data.db.migrations.manual.Migration13To14
import org.cryptomator.data.db.templating.DbTemplateModule
import org.cryptomator.data.db.templating.TemplateDatabaseContext
import org.cryptomator.data.util.useFinally
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "corruption-test"

@RunWith(AndroidJUnit4::class)
@SmallTest
class CorruptedDatabaseTest {

	private val context = InstrumentationRegistry.getInstrumentation().context
	private val sharedPreferencesHandler = SharedPreferencesHandler(context)
	private val openHelperFactory = DatabaseOpenHelperFactory { throw IllegalStateException() }
	private val templateDbStream = DbTemplateModule().let {
		it.provideDbTemplateStream(it.provideConfiguration(TemplateDatabaseContext(context)))
	}.also {
		require(it.markSupported())
		it.mark(it.available())
	}

	@Before
	fun setup() {
		context.getDatabasePath(TEST_DB).also { dbFile ->
			if (dbFile.exists()) {
				//This may happen when killing the process while using the debugger
				println("Test database \"${dbFile.absolutePath}\" not cleaned up. Deleting...")
				dbFile.delete()
			}
		}
	}

	@After
	fun tearDown() {
		context.getDatabasePath(TEST_DB).delete()
		templateDbStream.reset()
	}

	@Test
	fun testOpenVersion0Database() {
		val databaseModule = DatabaseModule()
		val migrations = arrayOf<Migration>(
			Upgrade1To2(),
			Upgrade2To3(context),
			Upgrade3To4(),
			Upgrade4To5(),
			Upgrade5To6(),
			Upgrade6To7(),
			Upgrade7To8(),
			Upgrade8To9(sharedPreferencesHandler),
			Upgrade9To10(sharedPreferencesHandler),
			Upgrade10To11(),
			Upgrade11To12(sharedPreferencesHandler),
			//
			Migration13To14(),
			//Auto: 14 -> 15
		)

		createVersion0Database(context, TEST_DB)
		databaseModule.provideInternalCryptomatorDatabase(
			context,
			migrations,
			{ templateDbStream },
			openHelperFactory,
			TEST_DB
		).useFinally({ db ->
			db.compileStatement("SELECT count(*) FROM `sqlite_master` WHERE `name` = 'CLOUD_ENTITY'").use { statement ->
				require(statement.simpleQueryForLong() == 1L)
			}
		}, finallyBlock = CryptomatorDatabase::close)
	}
}

@Suppress("serial")
private class InterruptCreationException : Exception()

private fun createVersion0Database(context: Context, databaseName: String) {
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

private fun initVersion0Database(openHelper: SupportSQLiteOpenHelper): Nothing {
	openHelper.writableDatabase.use {
		throw IllegalStateException("Creating a v0 database requires throwing an exception during creation (got ${it.version})")
	}
}