package org.cryptomator.data.db;

import android.content.Context;

import androidx.room.Room;

import org.cryptomator.data.db.dao.CachedFileDao;
import org.cryptomator.data.db.dao.CloudDao;
import org.cryptomator.data.db.dao.DocumentMetadataDao;
import org.cryptomator.data.db.dao.UpdateCheckDao;
import org.cryptomator.data.db.dao.VaultDao;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class DatabaseModule {

	@Singleton
	@Provides
	public CryptomatorDatabase provideCryptomatorDatabase(Context context, DatabaseUpgrades databaseUpgrades) {
		return Room.databaseBuilder(context, CryptomatorDatabase.class, CryptomatorDatabase.NAME) //
				.addMigrations(databaseUpgrades.all()) //
				.addCallback(new InitialDataCallback()) //
				// vaults and clouds are read from the main thread, e.g. in PhotoContentJob#onStartJob, and are only a
				// handful of rows
				.allowMainThreadQueries() //
				.build();
	}

	@Provides
	public CachedFileDao provideCachedFileDao(CryptomatorDatabase database) {
		return database.cachedFileDao();
	}

	@Provides
	public CloudDao provideCloudDao(CryptomatorDatabase database) {
		return database.cloudDao();
	}

	@Provides
	public DocumentMetadataDao provideDocumentMetadataDao(CryptomatorDatabase database) {
		return database.documentMetadataDao();
	}

	@Provides
	public UpdateCheckDao provideUpdateCheckDao(CryptomatorDatabase database) {
		return database.updateCheckDao();
	}

	@Provides
	public VaultDao provideVaultDao(CryptomatorDatabase database) {
		return database.vaultDao();
	}
}
