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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.nio.file.Files

@RunWith(AndroidJUnit4::class)
@SmallTest
class CreateDatabaseTest {

	private val context = InstrumentationRegistry.getInstrumentation().context

	@get:Rule
	val tempFolder: TemporaryFolder = TemporaryFolder()

	@Test
	fun testProvideDbTemplateStream() {
		val templateStream = DbTemplateModule().let { it.provideDbTemplateStream(it.provideConfiguration(TemplateDatabaseContext(context))) }
		val templateFile = tempFolder.newFolder("provideDbTemplateStream").resolve(DATABASE_NAME)
		Files.copy(templateStream, templateFile.toPath())
		val templateDatabaseContext = TemplateDatabaseContext(context).also {
			it.templateFile = templateFile
		}

		val config = SupportSQLiteOpenHelper.Configuration.builder(templateDatabaseContext) //
			.name(DATABASE_NAME) //
			.callback(object : SupportSQLiteOpenHelper.Callback(1) {
				override fun onConfigure(db: SupportSQLiteDatabase) = db.applyDefaultConfiguration( //
					writeAheadLoggingEnabled = false //
				)

				override fun onCreate(db: SupportSQLiteDatabase) = fail("Database should already exist")
				override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = fail("Database should already be target version")
			}).build()

		FrameworkSQLiteOpenHelperFactory().create(config).use { openHelper ->
			verifyDbTemplateStream(openHelper)
		}
	}

	private fun verifyDbTemplateStream(openHelper: SupportSQLiteOpenHelper) {
		openHelper.writableDatabase.use { templateDb ->
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

	@Test
	fun testProvideDbTemplateStreamFiles() {
		val templateDatabaseContext = TemplateDatabaseContext(context)
		assertNull(templateDatabaseContext.templateFile)
		DbTemplateModule().let { it.provideDbTemplateStream(it.provideConfiguration(templateDatabaseContext)) }.close()

		val templateFile = assertNotNullObj(templateDatabaseContext.templateFile)
		assertFalse(templateFile.exists())

		val parentDir = assertNotNullObj(templateFile.parentFile)
		assertFalse(parentDir.exists())
		assertEquals(requireNotNull(context.cacheDir), parentDir.parentFile)
	}
}

private fun <T> assertNotNullObj(obj: T?): T {
	//TODO Improve this method by using the kotlin contract API once it's stable
	assertNotNull(obj)
	return obj!!
}