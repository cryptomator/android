package org.cryptomator.data.cloud.googledrive;

import android.content.Context;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import org.cryptomator.data.BuildConfig;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.util.SharedPreferencesHandler;

import java.util.Collections;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import timber.log.Timber;

class GoogleDriveClientFactory {

	private final Context context;
	private final SharedPreferencesHandler sharedPreferencesHandler;

	GoogleDriveClientFactory(Context context, SharedPreferencesHandler sharedPreferencesHandler) {
		this.context = context;
		this.sharedPreferencesHandler = sharedPreferencesHandler;
	}

	Drive getClient(String accountName) throws FatalBackendException {
		if (sharedPreferencesHandler.debugMode()) {
			Logger.getLogger("com.google.api.client").setLevel(Level.CONFIG);
			Logger.getLogger("com.google.api.client").addHandler(new Handler() {
				@Override
				public void publish(LogRecord record) {
					if (record.getMessage().startsWith("-------------- RESPONSE --------------") //
							|| record.getMessage().startsWith("-------------- REQUEST  --------------") //
							|| record.getMessage().startsWith("{\n \"files\": [\n")) {
						Timber.tag("GoogleDriveClient").d(record.getMessage());
					}
				}

				@Override
				public void flush() {
				}

				@Override
				public void close() throws SecurityException {
				}
			});
		}

		try {
			FixedGoogleAccountCredential credential = FixedGoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE));
			credential.setAccountName(accountName);
			return new Drive.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential) //
					.setApplicationName("Cryptomator-Android/" + BuildConfig.VERSION_NAME) //
					.build();
		} catch (Exception e) {
			throw new FatalBackendException(e);
		}
	}
}
