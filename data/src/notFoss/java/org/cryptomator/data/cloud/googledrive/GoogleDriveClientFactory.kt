package org.cryptomator.data.cloud.googledrive

import android.content.Context
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import org.cryptomator.data.BuildConfig
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.util.SharedPreferencesHandler
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import timber.log.Timber

class GoogleDriveClientFactory internal constructor() {

	companion object {

		@Volatile
		private var instance: Drive? = null

		@Synchronized
		fun getInstance(accountName: String, context: Context): Drive = instance ?: createClient(accountName, context).also { instance = it }

		@Throws(FatalBackendException::class)
		fun createClient(accountName: String, context: Context): Drive {
			if (SharedPreferencesHandler(context).debugMode()) {
				Logger.getLogger("com.google.api.client").level = Level.CONFIG
				Logger.getLogger("com.google.api.client").addHandler(object : Handler() {
					override fun publish(record: LogRecord) {
						if (record.message.startsWith("-------------- RESPONSE --------------") //
							|| record.message.startsWith("-------------- REQUEST  --------------") //
							|| record.message.startsWith("{\n \"files\": [\n")
						) {
							Timber.tag("GoogleDriveClient").d(record.message)
						}
					}

					override fun flush() {}

					@Throws(SecurityException::class)
					override fun close() {
					}
				})
			}
			val credential = FixedGoogleAccountCredential.usingOAuth2(context, setOf(DriveScopes.DRIVE)).also { it.setAccountName(accountName) }
			return Drive.Builder(NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential) //
				.setApplicationName("Cryptomator-Android/" + BuildConfig.VERSION_NAME) //
				.build()

		}
	}
}
