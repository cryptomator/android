package org.cryptomator.data.cloud.pcloud

import android.content.Context
import com.pcloud.sdk.ApiClient
import com.pcloud.sdk.Authenticators
import com.pcloud.sdk.PCloudSdk
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor
import org.cryptomator.data.util.NetworkTimeout
import org.cryptomator.domain.PCloud
import org.cryptomator.util.crypto.CredentialCryptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

class PCloudClient private constructor() {

	companion object {

		fun createClient(pCloud: PCloud, context: Context): ApiClient {
			val okHttpClient = OkHttpClient() //
				.newBuilder() //
				.connectTimeout(NetworkTimeout.CONNECTION.timeout, NetworkTimeout.CONNECTION.unit) //
				.readTimeout(NetworkTimeout.READ.timeout, NetworkTimeout.READ.unit) //
				.writeTimeout(NetworkTimeout.WRITE.timeout, NetworkTimeout.WRITE.unit) //
				.addInterceptor(httpLoggingInterceptor(context)) //
				.build()

			return PCloudSdk //
				.newClientBuilder() //
				.authenticator(Authenticators.newOAuthAuthenticator(decrypt(pCloud.accessToken(), context))) //
				.withClient(okHttpClient) //
				.apiHost(pCloud.url()) //
				.create()
		}

		private fun decrypt(password: String, context: Context): String {
			return CredentialCryptor //
				.getInstance(context) //
				.decrypt(password)
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
