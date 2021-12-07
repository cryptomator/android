package org.cryptomator.data.cloud.dropbox

import android.content.Context
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.OkHttp3Requestor
import com.dropbox.core.v2.DbxClientV2
import org.cryptomator.data.BuildConfig
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor
import org.cryptomator.data.util.NetworkTimeout
import org.cryptomator.util.crypto.CredentialCryptor
import java.util.Locale
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

class DropboxClientFactory {

	companion object {

		@Volatile
		private var instance: DbxClientV2? = null

		@Synchronized
		fun getInstance(accessToken: String, context: Context): DbxClientV2 = instance ?: createDropboxClient(decrypt(accessToken, context), context).also { instance = it }

		private fun decrypt(password: String, context: Context): String {
			return CredentialCryptor.getInstance(context).decrypt(password)
		}

		private fun createDropboxClient(accessToken: String, context: Context): DbxClientV2 {
			val userLocale = Locale.getDefault().toString()

			val okHttpClient = OkHttpClient() //
				.newBuilder() //
				.connectTimeout(NetworkTimeout.CONNECTION.timeout, NetworkTimeout.CONNECTION.unit) //
				.readTimeout(NetworkTimeout.READ.timeout, NetworkTimeout.READ.unit) //
				.writeTimeout(NetworkTimeout.WRITE.timeout, NetworkTimeout.WRITE.unit) //
				.addInterceptor(httpLoggingInterceptor(context)) //
				.build()

			val requestConfig = DbxRequestConfig //
				.newBuilder("Cryptomator-Android/" + BuildConfig.VERSION_NAME) //
				.withUserLocale(userLocale) //
				.withHttpRequestor(OkHttp3Requestor(okHttpClient)) //
				.build()

			return DbxClientV2(requestConfig, accessToken)
		}

		private fun httpLoggingInterceptor(context: Context): Interceptor {
			val logger = object : HttpLoggingInterceptor.Logger {
				override fun log(message: String) {
					Timber.tag("OkHttp").d(message)
				}
			}
			return HttpLoggingInterceptor(logger, context)
		}

		fun logout() {
			instance = null
		}

	}
}
