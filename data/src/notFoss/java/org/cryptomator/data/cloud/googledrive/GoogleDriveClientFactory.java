package org.cryptomator.data.cloud.googledrive;

import android.content.Context;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import org.cryptomator.data.BuildConfig;
import org.cryptomator.domain.exception.FatalBackendException;

import java.util.Collections;

class GoogleDriveClientFactory {

	private final Context context;

	GoogleDriveClientFactory(Context context) {
		this.context = context;
	}

	Drive getClient(String accountName) throws FatalBackendException {
		try {
			FixedGoogleAccountCredential credential = FixedGoogleAccountCredential.usingOAuth2(context, Collections.singleton(DriveScopes.DRIVE));
			credential.setAccountName(accountName);
			return new Drive.Builder(AndroidHttp.newCompatibleTransport(), JacksonFactory.getDefaultInstance(), credential) //
					.setApplicationName("Cryptomator-Android/" + BuildConfig.VERSION_NAME) //
					.build();
		} catch (Exception e) {
			throw new FatalBackendException(e);
		}
	}
}
