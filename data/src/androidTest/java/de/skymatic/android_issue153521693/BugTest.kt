/*

MIT License

Copyright (c) 2024 Skymatic

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/
package de.skymatic.android_issue153521693

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// See https://github.com/cryptomator/android/issues/529 to learn more about this class.
// Also see https://github.com/SailReal/Android-Issue-153521693/tree/cfb5033cf287572ac4b4972700f1656ce2b61d03/src/androidTest/java/de/skymatic/android_issue153521693
@RunWith(AndroidJUnit4::class)
class BugTest {

	private val TEST_DB = "bug-test"

	private val context = InstrumentationRegistry.getInstrumentation().context

	private lateinit var db: SupportSQLiteDatabase

	@Before
	fun setup() {
		context.getDatabasePath(TEST_DB).delete() //Clean up last test

		//The bug doesn't seem to appear if everything is done is one session (at least with this suite), so let's simulate two sessions
		/* Database is created */
		SupportSQLiteOpenHelper.Configuration(context, TEST_DB,
			object : SupportSQLiteOpenHelper.Callback(1) {
				override fun onCreate(db: SupportSQLiteDatabase) = createDb(db)
				override fun onUpgrade(
					db: SupportSQLiteDatabase,
					oldVersion: Int,
					newVersion: Int
				) = throw AssertionError()
			}).let { FrameworkSQLiteOpenHelperFactory().create(it).writableDatabase }.close()

		/* Database is closed, e.g. the app has been closed */

		////////////////////////////////////////////

		/* Database is opened, not created */

		db = SupportSQLiteOpenHelper.Configuration(context, TEST_DB,
			object : SupportSQLiteOpenHelper.Callback(1) {
				override fun onCreate(db: SupportSQLiteDatabase) = throw AssertionError()
				override fun onUpgrade(
					db: SupportSQLiteDatabase,
					oldVersion: Int,
					newVersion: Int
				) = throw AssertionError()
			}).let { FrameworkSQLiteOpenHelperFactory().create(it).writableDatabase }
	}

	@After
	fun tearDown() {
		if (this::db.isInitialized) {
			db.close()
		}
	}

	private fun createDb(db: SupportSQLiteDatabase) {
		db.execSQL(
			"CREATE TABLE `TEST_TABLE` (" + //
					"`column0` TEXT NOT NULL," +
					"`column1` TEXT NOT NULL" +
					")"
		)
		db.execSQL("INSERT INTO `TEST_TABLE` (`column0`, `column1`) VALUES ('content0', 'content1')")

		db.version = 1
	}

	private fun modifySchema(db: SupportSQLiteDatabase) {
		db.execSQL("ALTER TABLE `TEST_TABLE` RENAME TO `TEST_TABLE_OLD`")
		db.execSQL(
			"CREATE TABLE `TEST_TABLE` (" + //
					"`column0` TEXT NOT NULL," +
					"`column1` TEXT NOT NULL," +
					"`column2` TEXT" +
					")"
		)
		db.execSQL("INSERT INTO `TEST_TABLE` (`column0`, `column1`) SELECT `column0`, `column1` FROM `TEST_TABLE_OLD`")
		db.execSQL("DROP TABLE `TEST_TABLE_OLD`")
		db.update(
			"TEST_TABLE",
			CONFLICT_NONE,
			ContentValues().also { it.put("column2", "content2"); },
			null,
			null
		)
	}

	private fun assertBroken(cursor: Cursor) {
		//cursor.moveToFirst() //This doesn't help because it only fixes *future* cursors
		assertEquals(2, cursor.columnCount) //Should be 3!
		assertArrayEquals(
			arrayOf("column0", "column1"),
			cursor.columnNames
		) //Should be ["column0", "column1", "column2"]
		assertEquals(-1, cursor.getColumnIndex("column2")) //Should be 2
	}

	private fun assertNotBroken(cursor: Cursor) {
		//cursor.moveToFirst() //Not needed and it wouldn't fix this cursor either way.
		assertEquals(3, cursor.columnCount)
		assertArrayEquals(arrayOf("column0", "column1", "column2"), cursor.columnNames)
		assertEquals(2, cursor.getColumnIndex("column2"))
	}

	@Test
	fun causeBugWithoutBackticks() {
		db.query("SELECT * FROM TEST_TABLE").close()

		modifySchema(db)

		db.query("SELECT * FROM TEST_TABLE").use {
			assertBroken(it)
		}
	}

	@Test
	fun causeBugWithBackticks() {
		db.query("SELECT * FROM `TEST_TABLE`").close()

		modifySchema(db)

		db.query("SELECT * FROM `TEST_TABLE`").use {
			assertBroken(it)
		}
	}

	@Test
	fun causeBugWithMove1() {
		db.query("SELECT * FROM `TEST_TABLE`").close()

		modifySchema(db)

		db.query("SELECT * FROM `TEST_TABLE`").use {
			it.moveToFirst() //This doesn't help because it only fixes *future* cursors
			assertBroken(it)
		}

		db.query("SELECT * FROM `TEST_TABLE`").use {
			//it.moveToFirst() //Not needed and it wouldn't fix this cursor either way.
			assertNotBroken(it)
		}
	}

	@Test
	fun causeBugWithMove2() {
		db.query("SELECT * FROM TEST_TABLE").close()
		db.query("SELECT * FROM `TEST_TABLE`").close()

		modifySchema(db)

		db.query("SELECT * FROM `TEST_TABLE`").use {
			assertBroken(it)
		}

		db.query("SELECT * FROM TEST_TABLE").use {
			it.moveToFirst() //This doesn't help because it only fixes *future* cursors
			assertBroken(it)
		}

		db.query("SELECT * FROM `TEST_TABLE`").use {
			assertBroken(it)
		}
	}

	@Test
	fun doNotCauseBugWithMixedBackticks1() {
		db.query("SELECT * FROM `TEST_TABLE`").close()

		modifySchema(db)

		db.query("SELECT * FROM TEST_TABLE").use {
			assertNotBroken(it)
		}
	}

	@Test
	fun doNotCauseBugWithMixedBackticks2() {
		db.query("SELECT * FROM TEST_TABLE").close()

		modifySchema(db)

		db.query("SELECT * FROM `TEST_TABLE`").use {
			assertNotBroken(it)
		}
	}

	@Test
	fun doNotCauseBugWithoutFirstCall() {
		modifySchema(db)

		db.query("SELECT * FROM `TEST_TABLE`").use {
			assertNotBroken(it)
		}
	}

	@Test
	fun doNotCauseBugWithRefresh() {
		db.query("SELECT * FROM `TEST_TABLE`").close()

		modifySchema(db)

		db.query("SELECT * FROM `TEST_TABLE`").use {
			it.count //Alternatively: it.moveToNext(), it.moveToFirst()
		}

		db.query("SELECT * FROM `TEST_TABLE`").use {
			assertNotBroken(it)
		}
	}
}