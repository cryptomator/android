package org.cryptomator.data.cloud.s3

import android.content.Context
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor
import org.cryptomator.data.util.NetworkTimeout
import org.cryptomator.domain.S3Cloud
import org.cryptomator.util.crypto.CredentialCryptor
import io.minio.MinioClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import timber.log.Timber

class S3Client private constructor() {

	companion object {

		fun createClient(cloud: S3Cloud, context: Context): MinioClient {
			val minioClientBuilder = MinioClient.builder()

			minioClientBuilder.endpoint(cloud.s3Endpoint())
			minioClientBuilder.region(cloud.s3Region())

			val httpClientBuilder = OkHttpClient() //
				.newBuilder() //
				.connectTimeout(NetworkTimeout.CONNECTION.timeout, NetworkTimeout.CONNECTION.unit) //
				.readTimeout(NetworkTimeout.READ.timeout, NetworkTimeout.READ.unit) //
				.writeTimeout(NetworkTimeout.WRITE.timeout, NetworkTimeout.WRITE.unit) //
				.addInterceptor(httpLoggingInterceptor(context))

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
	}
}
