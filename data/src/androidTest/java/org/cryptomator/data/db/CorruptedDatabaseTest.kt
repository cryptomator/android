package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.cryptomator.data.db.CryptomatorAssert.Order
import org.cryptomator.data.db.CryptomatorAssert.assertOrder
import org.cryptomator.data.db.migrations.MigrationContainer
import org.cryptomator.data.db.templating.DbTemplateModule
import org.cryptomator.data.db.templating.TemplateDatabaseContext
import org.cryptomator.data.util.useFinally
import org.cryptomator.util.SharedPreferencesHandler
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

private const val TEST_DB = "corruption-test"

@RunWith(AndroidJUnit4::class)
@SmallTest
class CorruptedDatabaseTest {

	private val context = InstrumentationRegistry.getInstrumentation().context
	private val sharedPreferencesHandler = SharedPreferencesHandler(context)
	private val templateDbStream = DbTemplateModule().let {
		it.provideDbTemplateStream(it.provideConfiguration(TemplateDatabaseContext(context)))
	}.also {
		require(it.markSupported())
		it.mark(it.available())
	}

	private lateinit var migrationContainer: MigrationContainer

	@Before
	fun setup() {
		context.getDatabasePath(TEST_DB).also { dbFile ->
			if (dbFile.exists()) {
				//This may happen when killing the process while using the debugger
				println("Test database \"${dbFile.absolutePath}\" not cleaned up. Deleting...")
				dbFile.delete()
			}
		}

		migrationContainer = createMigrationContainer(context, sharedPreferencesHandler)
	}

	@After
	fun tearDown() {
		context.getDatabasePath(TEST_DB).delete()
		templateDbStream.reset()
	}

	private fun createVersion0Database() {
		createVersion0Database(context, TEST_DB)
	}

	@Test
	fun testOpenVersion0Database() {
		createVersion0Database()
		DatabaseModule().provideInternalCryptomatorDatabase( //
			context, //
			migrationContainer.getPath(1).toTypedArray(), //
			{ templateDbStream }, //
			openHelperFactory(), //
			TEST_DB //
		).useFinally({ db ->
			db.compileStatement("SELECT count(*) FROM `sqlite_master` WHERE `name` = 'CLOUD_ENTITY'").use { statement ->
				require(statement.simpleQueryForLong() == 1L)
			}
		}, finallyBlock = CryptomatorDatabase::close)
	}

	@Test
	fun testOpenVersion0DatabaseVerifyStreamAccessed() {
		val order = Order()
		val templateStreamCallable = {
			assertOrder(order, 0)
			templateDbStream
		}
		val listener = object : InterceptorOpenHelperListener {
			override fun onWritableDatabaseCalled() {
				assertOrder(order, 1, 3, 4)
			}
		}

		createVersion0Database()
		DatabaseModule().provideInternalCryptomatorDatabase( //
			context, //
			migrationContainer.getPath(1).toTypedArray(), //
			templateStreamCallable, //
			InterceptorOpenHelperFactory(openHelperFactory(), listener), //
			TEST_DB //
		).useFinally({ db ->
			assertOrder(order, 2)
			db.compileStatement("SELECT count(*) FROM `sqlite_master` WHERE `name` = 'CLOUD_ENTITY'").use { statement ->
				require(statement.simpleQueryForLong() == 1L)
			}
		}, finallyBlock = CryptomatorDatabase::close)
		assertOrder(order, 5)
	}

	@Test
	fun testOpenDatabaseWithRecovery() {
		val order = Order()
		val templateStreamCallable = {
			assertOrder(order, 0)
			throw IOException()
		}
		val listener = object : InterceptorOpenHelperListener {
			override fun onWritableDatabaseCalled() {
				assertOrder(order, 1)
			}

			override fun onWritableDatabaseThrew(exc: Exception): Exception {
				assertOrder(order, 4)
				assertThat(exc, instanceOf(UnsupportedOperationException::class.java))
				return WrappedException(exc)
			}
		}
		val openHelperFactory = openHelperFactory {
			assertOrder(order, 2, 3)
		}

		createVersion0Database(context, TEST_DB)
		assertThrows(WrappedException::class.java) {
			DatabaseModule().provideInternalCryptomatorDatabase( //
				context, //
				migrationContainer.getPath(1).toTypedArray(), //
				templateStreamCallable, //
				InterceptorOpenHelperFactory(openHelperFactory, listener), //
				TEST_DB //
			).useFinally({ _ ->
				fail("Database initialization must throw")
			}, finallyBlock = CryptomatorDatabase::close)
		}.also {
			assertThat(it.cause, instanceOf(UnsupportedOperationException::class.java))
		}
		assertOrder(order, 5)
	}
}

private fun openHelperFactory(
	invalidationCallback: () -> Unit = { throw IllegalStateException() }
): DatabaseOpenHelperFactory {
	return DatabaseOpenHelperFactory(invalidationCallback)
}

private class InterceptorOpenHelperFactory(
	private val delegate: SupportSQLiteOpenHelper.Factory, //
	private val listener: InterceptorOpenHelperListener
) : SupportSQLiteOpenHelper.Factory {

	override fun create(
		configuration: SupportSQLiteOpenHelper.Configuration
	): SupportSQLiteOpenHelper {
		return InterceptorOpenHelper(delegate.create(configuration), listener)
	}
}

private class InterceptorOpenHelper(
	private val delegate: SupportSQLiteOpenHelper, //
	private val listener: InterceptorOpenHelperListener
) : SupportSQLiteOpenHelper by delegate {

	override val writableDatabase: SupportSQLiteDatabase
		get() {
			listener.onWritableDatabaseCalled()
			try {
				return delegate.writableDatabase
			} catch (exc: Exception) {
				throw listener.onWritableDatabaseThrew(exc)
			}
		}

	override val readableDatabase: SupportSQLiteDatabase
		get() = throw AssertionError()
}

private interface InterceptorOpenHelperListener {

	fun onWritableDatabaseCalled()
	fun onWritableDatabaseThrew(exc: Exception): Exception = exc

}

@Suppress("serial")
class WrappedException(cause: Exception) : Exception(cause)