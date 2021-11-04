package org.cryptomator.data.cloud.webdav.network

import android.content.Context
import android.net.ConnectivityManager
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import com.google.common.net.HttpHeaders
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor
import org.cryptomator.data.util.NetworkTimeout
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.exception.UnableToDecryptWebdavPasswordException
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.FatalCryptoException
import org.cryptomator.util.file.LruFileCacheUtil
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber

internal class WebDavCompatibleHttpClient(cloud: WebDavCloud, context: Context) {

	private val webDavRedirectHandler: WebDavRedirectHandler

	@Throws(IOException::class)
	fun execute(requestBuilder: Request.Builder): Response {
		return execute(requestBuilder.build())
	}

	@Throws(IOException::class)
	private fun execute(request: Request): Response {
		return webDavRedirectHandler.executeFollowingRedirects(request)
	}

	companion object {

		private fun httpClientFor(webDavCloud: WebDavCloud, context: Context, useLruCache: Boolean, lruCacheSize: Int): OkHttpClient {
			val authCache: Map<String, CachingAuthenticator> = ConcurrentHashMap()

			val builder = OkHttpClient() //
				.newBuilder() //
				.connectTimeout(NetworkTimeout.CONNECTION.timeout, NetworkTimeout.CONNECTION.unit) //
				.readTimeout(NetworkTimeout.READ.timeout, NetworkTimeout.READ.unit) //
				.writeTimeout(NetworkTimeout.WRITE.timeout, NetworkTimeout.WRITE.unit) //
				.followRedirects(false) //
				.addInterceptor(httpLoggingInterceptor(context)) //
				.authenticator(httpAuthenticator(context, webDavCloud, authCache)) //
				.addInterceptor(AuthenticationCacheInterceptor(authCache))
				.addInterceptor(UserAgentInterceptor())

			if (useLruCache) {
				val cache = Cache(LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.WEBDAV), lruCacheSize.toLong())
				builder.cache(cache) //
					.addNetworkInterceptor(provideCacheInterceptor()) //
					.addInterceptor(provideOfflineCacheInterceptor(context))
			}

			val trustManager = if (usingWebDavWithSelfSignedCertificate(webDavCloud)) {
				val pinningTrustManager = PinningTrustManager(webDavCloud.certificate())
				builder.hostnameVerifier(pinningTrustManager.hostnameVerifier())
				pinningTrustManager
			} else {
				DefaultTrustManager()
			}
			builder.sslSocketFactory(SSLSocketFactories.from(trustManager), trustManager)

			return builder.build()
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

		private fun provideCacheInterceptor(): Interceptor {
			return Interceptor { chain: Interceptor.Chain ->
				val response = chain.proceed(chain.request())
				val cacheControl = CacheControl.Builder() //
					.maxAge(0, TimeUnit.DAYS) //
					.build()
				response.newBuilder() //
					.removeHeader("Pragma") //
					.removeHeader("Cache-Control") //
					.header(HttpHeaders.CACHE_CONTROL, cacheControl.toString()) //
					.build()
			}
		}

		private fun httpAuthenticator(context: Context, webDavCloud: WebDavCloud, authCache: Map<String, CachingAuthenticator>): Authenticator {
			val credentials = Credentials(webDavCloud.username(), decryptPassword(context, webDavCloud.password()))
			val basicAuthenticator = BasicAuthenticator(credentials, StandardCharsets.UTF_8)
			val digestAuthenticator = DigestAuthenticator(credentials)
			var result: Authenticator = DispatchingAuthenticator.Builder() //
				.with("digest", digestAuthenticator) //
				.with("basic", basicAuthenticator) //
				.build()
			result = CachingAuthenticatorDecorator(result, authCache)
			return result
		}

		@Throws(UnableToDecryptWebdavPasswordException::class)
		private fun decryptPassword(context: Context, password: String): String {
			return try {
				CredentialCryptor //
					.getInstance(context) //
					.decrypt(password)
			} catch (e: FatalCryptoException) {
				throw UnableToDecryptWebdavPasswordException(e)
			}
		}

		private fun httpLoggingInterceptor(context: Context): Interceptor {
			val logger = object : HttpLoggingInterceptor.Logger {
				override fun log(message: String) {
					Timber.tag("OkHttp").d(message)
				}
			}

			return HttpLoggingInterceptor(logger, context)
		}

		private fun usingWebDavWithSelfSignedCertificate(webDavCloud: WebDavCloud): Boolean {
			return webDavCloud.certificate() != null
		}

		private fun isNetworkAvailable(context: Context): Boolean {
			val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
			val activeNetworkInfo = connectivityManager.activeNetworkInfo
			return activeNetworkInfo != null && activeNetworkInfo.isConnected
		}
	}

	class UserAgentInterceptor : Interceptor {

		@Throws(IOException::class)
		override fun intercept(chain: Interceptor.Chain): Response {
			val originalRequest: Request = chain.request()
			val userAgent = "davfs2/1.5.2"
			val requestWithUserAgent = originalRequest.newBuilder().header("User-Agent", userAgent).build()
			return chain.proceed(requestWithUserAgent)
		}
	}

	init {
		val sharedPreferencesHandler = SharedPreferencesHandler(context)
		webDavRedirectHandler = WebDavRedirectHandler(httpClientFor(cloud, context, sharedPreferencesHandler.useLruCache(), sharedPreferencesHandler.lruCacheSize()))
	}
}
