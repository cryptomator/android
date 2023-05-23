package org.cryptomator.data.cloud.onedrive

import android.content.Context
import com.microsoft.graph.authentication.BaseAuthenticationProvider
import com.microsoft.graph.httpcore.HttpClients
import com.microsoft.graph.requests.GraphServiceClient
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor
import org.cryptomator.data.util.NetworkTimeout
import org.cryptomator.util.crypto.CredentialCryptor
import java.net.URL
import java.util.concurrent.CompletableFuture
import okhttp3.Interceptor
import okhttp3.Request
import timber.log.Timber


class OnedriveClient private constructor() {

	companion object {

		fun createInstance(context: Context, encryptedToken: String): GraphServiceClient<Request> {
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

			val httpClient = HttpClients.createDefault(tokenAuthenticationProvider)
				.newBuilder()
				.connectTimeout(NetworkTimeout.CONNECTION.timeout, NetworkTimeout.CONNECTION.unit) //
				.readTimeout(NetworkTimeout.READ.timeout, NetworkTimeout.READ.unit) //
				.writeTimeout(NetworkTimeout.WRITE.timeout, NetworkTimeout.WRITE.unit) //
				.addInterceptor(httpLoggingInterceptor(context)) //
				.build();

			return GraphServiceClient //
				.builder() //
				.httpClient(httpClient) //
				.authenticationProvider(tokenAuthenticationProvider) //
				.buildClient()
		}

		private fun httpLoggingInterceptor(context: Context): Interceptor {
			val logger = object : HttpLoggingInterceptor.Logger {
				override fun log(message: String) {
					Timber.tag("OkHttp").d(message)
				}
			}
			return HttpLoggingInterceptor(logger, context)
		}
	}
}
