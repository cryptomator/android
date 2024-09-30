package org.cryptomator.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import org.cryptomator.data.db.SQLiteCacheControl.asCacheControlled
import org.cryptomator.data.db.migrations.MigrationContainer
import org.cryptomator.data.db.templating.DbTemplateComponent
import org.cryptomator.util.ThreadUtil
import org.cryptomator.util.named
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Callable
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton
import dagger.Module
import dagger.Provides
import timber.log.Timber

private val LOG = Timber.Forest.named("DatabaseModule")

@Module(subcomponents = [DbTemplateComponent::class])
class DatabaseModule {

	@Singleton
	@Provides
	fun provideCryptomatorDatabase(@DbInternal delegate: Provider<CryptomatorDatabase>): Invalidatable<CryptomatorDatabase> = Invalidatable {
		delegate.get()
	}

	@Singleton
	@Provides
	@Named("databaseInvalidationCallback")
	fun provideInvalidationCallback(invalidatable: Invalidatable<CryptomatorDatabase>): Function0<Unit> {
		return invalidatable::invalidate
	}

	@Provides
	@DbInternal
	internal fun provideInternalCryptomatorDatabase(
		context: Context, //
		@DbInternal migrations: Array<Migration>, //
		@DbInternal dbTemplateStreamCallable: Callable<InputStream>, //
		@DbInternal openHelperFactory: SupportSQLiteOpenHelper.Factory, //
		@DbInternal databaseName: String, //
	): CryptomatorDatabase {
		LOG.i("Building database (target version: %s)", CRYPTOMATOR_DATABASE_VERSION)
		ThreadUtil.assumeNotMainThread()
		return Room.databaseBuilder(context, CryptomatorDatabase::class.java, databaseName) //
			.createFromInputStream(dbTemplateStreamCallable) //
			.addMigrations(*migrations) //
			.addCallback(DatabaseCallback) //
			.openHelperFactory(openHelperFactory) //
			.setJournalMode(RoomDatabase.JournalMode.TRUNCATE) //
			.fallbackToDestructiveMigrationFrom(0) //
			.build() //Fails if no migration is found (especially when downgrading)
			.also { //
				//Migrations are only triggered once the database is used for the first time.
				//-- Let's do that now and verify all went well before returning the database.
				it.openHelper.writableDatabase.run {
					require(this.version == CRYPTOMATOR_DATABASE_VERSION)
					require(this.foreignKeyConstraintsEnabled)
				}
				LOG.i("Database built successfully")
			}
	}

	@Singleton
	@Provides
	@DbInternal
	fun provideDbTemplateStreamCallable(templateFactory: DbTemplateComponent.Factory): Callable<InputStream> = Callable {
		LOG.d("Creating database template stream")
		try {
			return@Callable templateFactory.create().templateStream()
		} catch (t: Throwable) {
			if (t !is IOException) {
				throw t
			}
			LOG.w(t, "IOException while reading database template, retrying...")
			try {
				return@Callable templateFactory.create().templateStream()
			} catch (tInner: Throwable) {
				tInner.addSuppressed(t)
				throw tInner
			}
		}
	}

	@Singleton
	@Provides
	@DbInternal
	internal fun provideOpenHelperFactory(openHelperFactory: DatabaseOpenHelperFactory): SupportSQLiteOpenHelper.Factory {
		return openHelperFactory.asCacheControlled()
	}

	@Singleton
	@Provides
	@DbInternal
	internal fun provideDatabaseName(): String = DATABASE_NAME

	@Singleton
	@Provides
	fun provideCloudDao(database: Invalidatable<CryptomatorDatabase>): CloudDao {
		return DelegatingCloudDao(database)
	}

	@Singleton
	@Provides
	fun provideUpdateCheckDao(database: Invalidatable<CryptomatorDatabase>): UpdateCheckDao {
		return DelegatingUpdateCheckDao(database)
	}

	@Singleton
	@Provides
	fun provideVaultDao(database: Invalidatable<CryptomatorDatabase>): VaultDao {
		return DelegatingVaultDao(database)
	}

	@Singleton
	@Provides
	@DbInternal
	internal fun provideMigrations(migrationContainer: MigrationContainer): Array<Migration> {
		return migrationContainer.getPath(1).toTypedArray()
	}
}

object DatabaseCallback : RoomDatabase.Callback() {

	override fun onCreate(db: SupportSQLiteDatabase) {
		//This should not be called except if there was corruption and the recovery in CopyOpenHelper failed; in that case PatchedCallback will invalidate the db
		throw UnsupportedOperationException("Creation is handled as upgrade")
	}

	override fun onOpen(db: SupportSQLiteDatabase) {
		LOG.i("Opened database (v%s)", db.version)
	}

	override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
		//This should not be called
		throw UnsupportedOperationException("Destructive migration is not supported")
	}
}

@Qualifier
@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
private annotation class DbInternal