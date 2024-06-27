package org.cryptomator.data.db.templating

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.cryptomator.data.db.DATABASE_NAME
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
@SmallTest
class TemplateDatabaseContextTest {

	private val baseContext = InstrumentationRegistry.getInstrumentation().context

	@Test(expected = IllegalArgumentException::class)
	fun testTempDatabaseContextIllegalName() {
		TemplateDatabaseContext(baseContext).getDatabasePath("Database42")
	}

	@Test(expected = IllegalArgumentException::class)
	fun testTempDatabaseContextIllegalNameFileSet() {
		val templateDbContext = TemplateDatabaseContext(baseContext)
		templateDbContext.templateFile = File("/test/12345/Database")
		templateDbContext.getDatabasePath("Database42")
	}

	@Test
	fun testTempDatabaseContextTemplateFileNotSet() {
		val templateDbContext = TemplateDatabaseContext(baseContext)
		assertNull(templateDbContext.templateFile)
	}

	@Test(expected = IllegalArgumentException::class)
	fun testTempDatabaseContextTemplateFileNotSetThrows() {
		val templateDbContext = TemplateDatabaseContext(baseContext)
		templateDbContext.getDatabasePath(DATABASE_NAME)
	}

	@Test(expected = IllegalArgumentException::class)
	fun testTempDatabaseContextFileSetTwice() {
		val templateDbContext = TemplateDatabaseContext(baseContext)
		assertNull(templateDbContext.templateFile)

		val actualTemplateFile = File("/test/12345/Database")
		templateDbContext.templateFile = actualTemplateFile
		assertSame(actualTemplateFile, templateDbContext.templateFile)

		templateDbContext.templateFile = File("/test/67890/Throws")
	}

	@Test(expected = IllegalArgumentException::class)
	fun testTempDatabaseContextFileSetWithNull() {
		val templateDbContext = TemplateDatabaseContext(baseContext)
		templateDbContext.templateFile = null
	}

	@Test
	fun testTempDatabaseContext() {
		val templateDbContext = TemplateDatabaseContext(baseContext)
		assertNull(templateDbContext.templateFile)

		val actualTemplateFile = File("/test/12345/Database")
		templateDbContext.templateFile = actualTemplateFile

		val invocation1 = templateDbContext.getDatabasePath(DATABASE_NAME)
		assertSame(actualTemplateFile, invocation1)
		val invocation2 = templateDbContext.getDatabasePath(DATABASE_NAME)
		assertSame(actualTemplateFile, invocation2)
	}
}