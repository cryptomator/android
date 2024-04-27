package org.cryptomator.data.db

import androidx.room.util.useCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.cryptomator.data.db.migrations.Sql
import org.cryptomator.data.db.templating.DbTemplateModule
import org.cryptomator.data.db.templating.TemplateDatabaseContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class CreateDatabaseTest {

	private val context = InstrumentationRegistry.getInstrumentation().context

	@Test
	fun testProvideDbTemplateFile() {
		val templateDatabaseContext = TemplateDatabaseContext(context)
		val templateFile = DbTemplateModule().let { it.provideDbTemplateFile(it.provideConfiguration(templateDatabaseContext)) }
		assertTrue(templateFile.exists())

		val templateDb = SupportSQLiteOpenHelper.Configuration(templateDatabaseContext, DATABASE_NAME, object : SupportSQLiteOpenHelper.Callback(1) {
			override fun onCreate(db: SupportSQLiteDatabase) = fail("Database should already exist")
			override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = fail("Database should already be target version")
		}).let { FrameworkSQLiteOpenHelperFactory().create(it).writableDatabase }
		assertEquals(1, templateDb.version)

		val elements = mutableListOf("CLOUD_ENTITY", "VAULT_ENTITY", "IDX_VAULT_ENTITY_FOLDER_PATH_FOLDER_CLOUD_ID")
		Sql.query("sqlite_master") //
			.columns(listOf("name")) //
			.where("name", Sql.notEq("android_metadata")) //
			.executeOn(templateDb) //
			.useCursor {
				while (it.moveToNext()) {
					val elementName = it.getString(it.getColumnIndex("name"))
					assertTrue("Unknown/Duplicate element: \"$elementName\"", elements.remove(elementName))
				}
				assertTrue("Missing element(s): ${elements.joinToString(prefix = "\"", postfix = "\"")}", elements.isEmpty())
			}
	}
}