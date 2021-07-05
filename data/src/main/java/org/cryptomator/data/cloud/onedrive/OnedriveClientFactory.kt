package org.cryptomator.data.cloud.onedrive

import android.content.Context
import com.microsoft.graph.authentication.IAuthenticationProvider
import com.microsoft.graph.core.DefaultClientConfig
import com.microsoft.graph.models.extensions.IGraphServiceClient
import com.microsoft.graph.requests.extensions.GraphServiceClient
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor
import org.cryptomator.data.cloud.onedrive.graph.MSAAuthAndroidAdapter
import org.cryptomator.data.util.NetworkTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

class OnedriveClientFactory private constructor() {

	companion object {

		@Volatile
		private var instance: IGraphServiceClient? = null

		@Volatile
		private var authenticationAdapter: MSAAuthAndroidAdapter? = null

		@Synchronized
		fun getInstance(context: Context, refreshToken: String?): IGraphServiceClient = instance ?: createClient(context, refreshToken).also { instance = it }

		@Synchronized
		fun getAuthAdapter(context: Context, refreshToken: String?): MSAAuthAndroidAdapter = authenticationAdapter ?: MSAAuthAndroidAdapterImpl(context, refreshToken).also { authenticationAdapter = it }

		private fun createClient(context: Context, refreshToken: String?): IGraphServiceClient {
			val builder = OkHttpClient() //
				.newBuilder() //
				.connectTimeout(NetworkTimeout.CONNECTION.timeout, NetworkTimeout.CONNECTION.unit) //
				.readTimeout(NetworkTimeout.READ.timeout, NetworkTimeout.READ.unit) //
				.writeTimeout(NetworkTimeout.WRITE.timeout, NetworkTimeout.WRITE.unit) //
				.addInterceptor(httpLoggingInterceptor(context))

			val onedriveHttpProvider = OnedriveHttpProvider(object : DefaultClientConfig() {
				override fun getAuthenticationProvider(): IAuthenticationProvider {
					return getAuthAdapter(context, refreshToken)
				}
			}, builder.build())

			return GraphServiceClient //
				.builder() //
				.authenticationProvider(authenticationAdapter) //
				.httpProvider(onedriveHttpProvider) //
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
