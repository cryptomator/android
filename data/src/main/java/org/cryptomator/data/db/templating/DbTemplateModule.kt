package org.cryptomator.data.db.templating

import android.content.Context
import android.content.ContextWrapper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.cryptomator.data.db.DATABASE_NAME
import org.cryptomator.data.db.migrations.legacy.Upgrade0To1
import org.cryptomator.util.ThreadUtil
import org.cryptomator.util.named
import java.io.File
import java.nio.file.Files
import javax.inject.Inject
import dagger.Module
import dagger.Provides
import timber.log.Timber

private val LOG = Timber.Forest.named("DbTemplateModule")

@Module
class DbTemplateModule {

	@DbTemplateScoped
	@Provides
	internal fun provideDbTemplateFile(configuration: SupportSQLiteOpenHelper.Configuration): File {
		LOG.d("Creating database template file")
		ThreadUtil.assumeNotMainThread()
		val db = configuration.let { FrameworkSQLiteOpenHelperFactory().create(it).writableDatabase }
		require(db.version == 1)
		db.close()

		LOG.d("Created database template file")
		return File(requireNotNull(db.path))
	}

	@DbTemplateScoped
	@Provides
	internal fun provideConfiguration(templateDatabaseContext: TemplateDatabaseContext): SupportSQLiteOpenHelper.Configuration {
		return SupportSQLiteOpenHelper.Configuration.builder(templateDatabaseContext) //
			.name(DATABASE_NAME) //
			.callback(object : SupportSQLiteOpenHelper.Callback(1) {
				override fun onConfigure(db: SupportSQLiteDatabase) {
					db.disableWriteAheadLogging()
					db.setForeignKeyConstraintsEnabled(true)
				}

				override fun onCreate(db: SupportSQLiteDatabase) {
					Upgrade0To1().migrate(db)
				}

				override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
					throw IllegalStateException("Template may not be upgraded")
				}
			}).build()
	}
}

@DbTemplateScoped
internal class TemplateDatabaseContext @Inject constructor(context: Context) : ContextWrapper(context) {

	private val dbFile: File by lazy {
		return@lazy Files.createTempDirectory(cacheDir.toPath(), "DbTemplate").resolve(DATABASE_NAME).toFile()
	}

	override fun getDatabasePath(name: String?): File {
		require(name == DATABASE_NAME)
		return dbFile
	}
}