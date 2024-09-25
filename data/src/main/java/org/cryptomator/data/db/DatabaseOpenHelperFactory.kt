package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.cryptomator.data.util.useFinally
import org.cryptomator.util.named
import java.io.File
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import timber.log.Timber

private val LOG = Timber.Forest.named("DatabaseOpenHelperFactory")

//This needs to stay in sync with UpgradeDatabaseTest#setup
@Singleton
internal class DatabaseOpenHelperFactory(
	private val invalidationCallback: Function0<Unit>, //
	private val delegate: SupportSQLiteOpenHelper.Factory
) : SupportSQLiteOpenHelper.Factory {

	@Inject
	constructor(@Named("databaseInvalidationCallback") invalidationCallback: Function0<Unit>) : this(invalidationCallback, FrameworkSQLiteOpenHelperFactory())

	override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
		LOG.d("Creating SupportSQLiteOpenHelper for database \"${configuration.name}\"")
		return delegate.create(patchConfiguration(invalidationCallback, configuration))
	}
}

private fun patchConfiguration(invalidationCallback: Function0<Unit>, configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper.Configuration {
	return SupportSQLiteOpenHelper.Configuration(
		context = configuration.context,
		name = configuration.name,
		callback = PatchedCallback(invalidationCallback, configuration.callback),
		useNoBackupDirectory = configuration.useNoBackupDirectory,
		allowDataLossOnRecovery = configuration.allowDataLossOnRecovery
	)
}

private class PatchedCallback(
	private val invalidationCallback: Function0<Unit>,
	private val delegateCallback: SupportSQLiteOpenHelper.Callback,
) : SupportSQLiteOpenHelper.Callback(delegateCallback.version) {

	override fun onConfigure(db: SupportSQLiteDatabase) {
		LOG.d("Called onConfigure for \"${db.path}\"@${db.version}")
		require(!db.isWriteAheadLoggingEnabled) { "WAL for \"${db.path}\" should already be disabled by room" }
		db.applyDefaultConfiguration( //
			writeAheadLoggingEnabled = null //WAL is handled by Room
		)
		//
		delegateCallback.onConfigure(db)
		//
	}

	override fun onCreate(db: SupportSQLiteDatabase) {
		//This should not be called except if there was corruption and the recovery in CopyOpenHelper failed; in that case invalidate the db
		LOG.e(Exception(), "Called onCreate for \"${db.path}\"@${db.version}")
		invalidationCallback.invoke()
		//
		delegateCallback.onCreate(db) //Callback from DatabaseModule will throw here
		//
	}

	override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
		LOG.i("Called onUpgrade for \"${db.path}\"@${db.version} ($oldVersion -> $newVersion)")
		//
		delegateCallback.onUpgrade(db, oldVersion, newVersion)
		//
	}

	override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
		LOG.e(Exception(), "Called onDowngrade for \"${db.path}\"@${db.version} ($oldVersion -> $newVersion)")
		//
		delegateCallback.onDowngrade(db, oldVersion, newVersion)
		//
	}

	override fun onCorruption(db: SupportSQLiteDatabase) = useFinally({
		LOG.e(Exception(), "Called onCorruption for \"${db.path}\"")
		//
		delegateCallback.onCorruption(db)
		//
	}, finallyBlock = {
		invalidationCallback.invoke()

		logCorruptedDbState(db)
	})

	private fun logCorruptedDbState(db: SupportSQLiteDatabase) {
		val state = db.path?.let { path ->
			if (path == ":memory:") null else path
		}.runCatching {
			this?.let { path -> (File(path).exists()) }
		}.map {
			when (it) {
				null -> "In memory"
				true -> "Exists"
				false -> "Deleted"
			}
		}.onFailure { verificationFailure ->
			LOG.e(verificationFailure, "Couldn't verify state of database \"${db.path}\"")
		}.getOrDefault("Unknown (see above)")
		LOG.e(Exception(), "State of \"${db.path}\": $state")
	}

	override fun onOpen(db: SupportSQLiteDatabase) {
		//
		delegateCallback.onOpen(db)
		//
	}
}