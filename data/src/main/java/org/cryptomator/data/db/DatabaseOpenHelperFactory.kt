package org.cryptomator.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.cryptomator.util.named
import timber.log.Timber

private val LOG = Timber.Forest.named("DatabaseOpenHelperFactory")

//This needs to stay in sync with UpgradeDatabaseTest#setup
internal class DatabaseOpenHelperFactory(
	private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
) : SupportSQLiteOpenHelper.Factory {

	override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
		LOG.d("Creating SupportSQLiteOpenHelper for database \"${configuration.name}\"")
		return delegate.create(patchConfiguration(configuration))
	}
}

private fun patchConfiguration(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper.Configuration {
	return SupportSQLiteOpenHelper.Configuration(
		context = configuration.context,
		name = configuration.name,
		callback = PatchedCallback(configuration.callback),
		useNoBackupDirectory = configuration.useNoBackupDirectory,
		allowDataLossOnRecovery = configuration.allowDataLossOnRecovery
	)
}

private class PatchedCallback(
	private val delegateCallback: SupportSQLiteOpenHelper.Callback,
) : SupportSQLiteOpenHelper.Callback(delegateCallback.version) {

	override fun onConfigure(db: SupportSQLiteDatabase) {
		LOG.d("Called onConfigure for \"${db.path}\"@${db.version}")
		db.setForeignKeyConstraintsEnabled(true)
		//
		delegateCallback.onConfigure(db)
		//
	}

	override fun onCreate(db: SupportSQLiteDatabase) {
		LOG.e(Exception(), "Called onCreate for \"${db.path}\"@${db.version}")
		//
		delegateCallback.onCreate(db)
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

	override fun onCorruption(db: SupportSQLiteDatabase) {
		//
		delegateCallback.onCorruption(db)
		//
	}

	override fun onOpen(db: SupportSQLiteDatabase) {
		//
		delegateCallback.onOpen(db)
		//
	}
}