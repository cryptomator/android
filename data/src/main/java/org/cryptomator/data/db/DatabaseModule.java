package org.cryptomator.data.db;

import android.content.Context;

import androidx.room.Room;

import org.cryptomator.data.db.dao.CloudDao;
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
				// the database holds a handful of rows and is read from the main thread in places such as
				// PhotoContentJob#onStartJob, which greenDAO allowed and moving off it is its own change
				.allowMainThreadQueries() //
				.build();
	}

	@Provides
	public CloudDao provideCloudDao(CryptomatorDatabase database) {
		return database.cloudDao();
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
