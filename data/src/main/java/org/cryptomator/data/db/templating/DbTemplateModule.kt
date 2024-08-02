package org.cryptomator.data.db.templating

import android.content.Context
import android.content.ContextWrapper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.cryptomator.data.db.DATABASE_NAME
import org.cryptomator.data.db.applyDefaultConfiguration
import org.cryptomator.data.db.migrations.legacy.Upgrade0To1
import org.cryptomator.data.util.useFinally
import org.cryptomator.util.ThreadUtil
import org.cryptomator.util.named
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import javax.inject.Inject
import dagger.Module
import dagger.Provides
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import timber.log.Timber

private val LOG = Timber.Forest.named("DbTemplateModule")

@Module
class DbTemplateModule {

	@DbTemplateScoped
	@Provides
	internal fun provideDbTemplateStream(configuration: SupportSQLiteOpenHelper.Configuration): InputStream {
		LOG.d("Creating database template file")
		ThreadUtil.assumeNotMainThread()

		val templateDatabaseContext = configuration.context
		require(templateDatabaseContext is TemplateDatabaseContext)
		var parentDir: Path? = null
		return useFinally({
			parentDir = Files.createTempDirectory(configuration.context.cacheDir.toPath(), "DbTemplate")
			val templateFile: Path = parentDir!!.resolve(DATABASE_NAME)
			templateDatabaseContext.templateFile = templateFile.toFile()

			val initializedPath = FrameworkSQLiteOpenHelperFactory().create(configuration).use { openHelper ->
				openHelper.setWriteAheadLoggingEnabled(false)
				initDatabase(openHelper)
			}
			verifyTemplate(templateFile, Path(requireNotNull(initializedPath)))

			val length: Long = templateFile.fileSize()
			require(length > 0L && length < Int.MAX_VALUE.toLong()) { "Template database file must be readable and smaller than 2 GB; template file at \"$templateFile\" is $length B long" }
			LOG.d("Created database template file ($length B) at \"$templateFile\"")

			//If this method throws an OutOfMemoryError, the db template mostly likely was larger than 2GB
			return@useFinally templateFile.readBytes().inputStream().also {
				LOG.d("Created database template stream (${it.available()} B) from \"$templateFile\"")
			}
		}, finallyBlock = {
			try {
				if (parentDir?.toFile()?.deleteRecursively() == false) {
					LOG.w("Failed to clean up template database file in \"$parentDir\"")
				}
			} catch (e: Exception) {
				LOG.e(e, "Exception while cleaning up template database file in \"$parentDir\"")
			}
		})
	}

	private fun initDatabase(openHelper: SupportSQLiteOpenHelper): String? {
		return openHelper.writableDatabase.use {
			require(it.version == 1)
			require(it.compileStatement("SELECT COUNT(*) FROM `CLOUD_ENTITY`").simpleQueryForLong() == 4L)
			it.path
		}
	}

	private fun verifyTemplate(templateFile: Path, initializedPath: Path) {
		require(Files.isSameFile(templateFile, initializedPath))
		require(templateFile.isRegularFile(LinkOption.NOFOLLOW_LINKS))
		if (templateFile != initializedPath) {
			LOG.i("\"$templateFile\" was initialized at different path (\"$initializedPath\")")
		}
	}

	@DbTemplateScoped
	@Provides
	internal fun provideConfiguration(templateDatabaseContext: TemplateDatabaseContext): SupportSQLiteOpenHelper.Configuration {
		return SupportSQLiteOpenHelper.Configuration.builder(templateDatabaseContext) //
			.name(DATABASE_NAME) //
			.callback(object : SupportSQLiteOpenHelper.Callback(1) {
				override fun onConfigure(db: SupportSQLiteDatabase) = db.applyDefaultConfiguration( //
					assertedWalEnabledStatus = false //
				)

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

	internal var templateFile: File?
		get() = _templateFile
		set(value) {
			require(_templateFile == null)
			requireNotNull(value)
			_templateFile = value
		}

	private var _templateFile: File? = null

	override fun getDatabasePath(name: String?): File {
		require(name == DATABASE_NAME)
		return requireNotNull(templateFile) { "Template file should be set by \"provideDbTemplateStream\"" }
	}
}