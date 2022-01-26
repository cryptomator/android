package org.cryptomator.data.cloud.onedrive

import android.content.Context
import com.microsoft.graph.authentication.BaseAuthenticationProvider
import com.microsoft.graph.logger.ILogger
import com.microsoft.graph.logger.LoggerLevel
import com.microsoft.graph.requests.GraphServiceClient
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CredentialCryptor
import java.net.URL
import java.util.concurrent.CompletableFuture
import okhttp3.Request
import timber.log.Timber


class OnedriveClientFactory private constructor() {

	companion object {

		fun createInstance(context: Context, encryptedToken: String, sharedPreferencesHandler: SharedPreferencesHandler): GraphServiceClient<Request> {
			val tokenAuthenticationProvider = object : BaseAuthenticationProvider() {
				val token = CompletableFuture.completedFuture(CredentialCryptor.getInstance(context).decrypt(encryptedToken))
				override fun getAuthorizationTokenAsync(requestUrl: URL): CompletableFuture<String> {
					return if (shouldAuthenticateRequestWithUrl(requestUrl)) {
						token
					} else {
						CompletableFuture.completedFuture(null)
					}
				}
			}

			val logger = object : ILogger {
				override fun getLoggingLevel(): LoggerLevel {
					return if(sharedPreferencesHandler.debugMode()) {
						LoggerLevel.DEBUG
					} else {
						LoggerLevel.ERROR
					}
				}

				override fun logDebug(message: String) {
					Timber.tag("OnedriveClientFactory").d(message)
				}

				override fun logError(message: String, throwable: Throwable?) {
					Timber.tag("OnedriveClientFactory").e(throwable, message)
				}

				override fun setLoggingLevel(level: LoggerLevel) {}
			}

			return GraphServiceClient //
				.builder() //
				.authenticationProvider(tokenAuthenticationProvider) //
				.logger(logger)
				.buildClient()
		}
	}
}
