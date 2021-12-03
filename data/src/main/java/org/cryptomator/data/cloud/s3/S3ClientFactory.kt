package org.cryptomator.data.cloud.s3

import android.content.Context
import android.net.ConnectivityManager
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor
import org.cryptomator.data.util.NetworkTimeout
import org.cryptomator.domain.S3Cloud
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.file.LruFileCacheUtil
import java.util.concurrent.TimeUnit
import io.minio.MinioClient
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

class S3ClientFactory private constructor() {

	companion object {

		@Volatile
		private var instance: MinioClient? = null

		@Synchronized
		fun getInstance(context: Context, cloud: S3Cloud): MinioClient = instance ?: createClient(context, cloud).also { instance = it }

		private fun createClient(context: Context, cloud: S3Cloud): MinioClient {
			val sharedPreferencesHandler = SharedPreferencesHandler(context)
			val minioClientBuilder = MinioClient.builder()

			minioClientBuilder.endpoint(cloud.s3Endpoint())
			minioClientBuilder.region(cloud.s3Region())

			val httpClientBuilder = OkHttpClient() //
				.newBuilder() //
				.connectTimeout(NetworkTimeout.CONNECTION.timeout, NetworkTimeout.CONNECTION.unit) //
				.readTimeout(NetworkTimeout.READ.timeout, NetworkTimeout.READ.unit) //
				.writeTimeout(NetworkTimeout.WRITE.timeout, NetworkTimeout.WRITE.unit) //
				.addInterceptor(httpLoggingInterceptor(context))

			if (sharedPreferencesHandler.useLruCache()) {
				val cache = Cache(LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.S3), sharedPreferencesHandler.lruCacheSize().toLong())
				httpClientBuilder.cache(cache).addInterceptor(provideOfflineCacheInterceptor(context))
			}

			return minioClientBuilder //
				.credentials(decrypt(cloud.accessKey(), context), decrypt(cloud.secretKey(), context)) //
				.httpClient(httpClientBuilder.build()) //
				.build()
		}

		private fun decrypt(password: String, context: Context): String {
			return CredentialCryptor.getInstance(context).decrypt(password)
		}

		private fun httpLoggingInterceptor(context: Context): Interceptor {
			val logger = object : HttpLoggingInterceptor.Logger {
				override fun log(message: String) {
					Timber.tag("OkHttp").d(message)
				}
			}

			return HttpLoggingInterceptor(logger, context)
		}

		private fun provideOfflineCacheInterceptor(context: Context): Interceptor {
			return Interceptor { chain: Interceptor.Chain ->
				var request = chain.request()
				if (isNetworkAvailable(context)) {
					val cacheControl = CacheControl.Builder() //
						.maxAge(0, TimeUnit.DAYS) //
						.build()
					request = request.newBuilder() //
						.cacheControl(cacheControl) //
						.build()
				}
				chain.proceed(request)
			}
		}

		private fun isNetworkAvailable(context: Context): Boolean {
			val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
			val activeNetworkInfo = connectivityManager.activeNetworkInfo
			return activeNetworkInfo != null && activeNetworkInfo.isConnected
		}

		@Synchronized
		fun logout() {
			instance = null
		}
	}
}
