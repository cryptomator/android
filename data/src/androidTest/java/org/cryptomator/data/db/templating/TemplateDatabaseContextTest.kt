package org.cryptomator.data.db.templating

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.cryptomator.data.db.DATABASE_NAME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class TemplateDatabaseContextTest {

	private val baseContext = InstrumentationRegistry.getInstrumentation().context

	@Test(expected = IllegalArgumentException::class)
	fun testTempDatabaseContextIllegalName() {
		TemplateDatabaseContext(baseContext).getDatabasePath("Database42")
	}

	@Test
	fun testTempDatabaseContext() {
		val templateDbContext = TemplateDatabaseContext(baseContext)
		val templatePath = templateDbContext.getDatabasePath(DATABASE_NAME)
		templatePath.parentFile.let { tempDir ->
			assertNotNull(tempDir)
			assertEquals(baseContext.cacheDir, tempDir!!.parentFile)
		}

		val secondInvocation = templateDbContext.getDatabasePath(DATABASE_NAME)
		assertSame(templatePath, secondInvocation)
	}
}