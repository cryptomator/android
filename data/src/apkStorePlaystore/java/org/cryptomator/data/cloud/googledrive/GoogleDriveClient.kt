package org.cryptomator.data.cloud.googledrive

import android.content.Context
import com.google.api.client.http.HttpBackOffIOExceptionHandler
import com.google.api.client.http.HttpBackOffUnsuccessfulResponseHandler
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import org.cryptomator.data.BuildConfig
import org.cryptomator.util.SharedPreferencesHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import timber.log.Timber

class GoogleDriveClient private constructor() {

	companion object {

		fun createClient(accountName: String, context: Context): Drive {
			if (SharedPreferencesHandler(context).debugMode()) {
				Logger.getLogger("com.google.api.client").level = Level.CONFIG
				Logger.getLogger("com.google.api.client").addHandler(object : Handler() {
					override fun publish(record: LogRecord) {
						Timber.tag("GoogleDriveClient").d(record.message)
					}

					override fun flush() {}

					@Throws(SecurityException::class)
					override fun close() {
					}
				})
			}
			val credential = FixedGoogleAccountCredential.usingOAuth2(context, setOf(DriveScopes.DRIVE)).also { it.setAccountName(accountName) }
			return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential) //
				.setApplicationName("Cryptomator-Android/" + BuildConfig.VERSION_NAME) //
				.setHttpRequestInitializer { request ->
					credential.initialize(request)

					val exponentialBackOff = ExponentialBackOff.Builder().setMaxElapsedTimeMillis(15 * 1000).build()
					request.unsuccessfulResponseHandler = HttpBackOffUnsuccessfulResponseHandler(exponentialBackOff).setBackOffRequired { response ->
						response.statusCode == 403 || response.statusCode / 100 == 5
					}
					request.ioExceptionHandler = HttpBackOffIOExceptionHandler(exponentialBackOff)

					// trim down logging
					request.isCurlLoggingEnabled = false
					request.contentLoggingLimit = 0
				}
				.build()
		}
	}
}
