package org.cryptomator.data.cloud.box

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.cryptomator.data.cloud.okhttplogging.HttpLoggingInterceptor
import org.cryptomator.data.util.NetworkTimeout
import org.cryptomator.data.util.UserAgentInterceptor
import org.cryptomator.domain.exception.FatalBackendException
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import timber.log.Timber

internal object BoxHttpClient {

	val gson = Gson()

	fun create(context: Context): OkHttpClient {
		return OkHttpClient() //
			.newBuilder() //
			.connectTimeout(NetworkTimeout.CONNECTION.timeout, NetworkTimeout.CONNECTION.unit) //
			.readTimeout(NetworkTimeout.READ.timeout, NetworkTimeout.READ.unit) //
			.writeTimeout(NetworkTimeout.WRITE.timeout, NetworkTimeout.WRITE.unit) //
			.addInterceptor(httpLoggingInterceptor(context)) //
			.addInterceptor(UserAgentInterceptor()) //
			.build()
	}

	/**
	 * Returns the parsed body of a successful response and throws a [BoxApiException] for any other, closing the response in both cases.
	 */
	@Throws(IOException::class)
	fun <T> parse(response: Response, type: Class<T>): T {
		response.use {
			val body = it.body?.string() ?: ""
			if (!it.isSuccessful) {
				throw apiException(it.code, body)
			}
			return gson.fromJson(body, type) ?: throw FatalBackendException("Box responded with an empty body")
		}
	}

	@Throws(IOException::class)
	fun requireSuccess(response: Response) {
		response.use {
			if (!it.isSuccessful) {
				throw apiException(it.code, it.body?.string() ?: "")
			}
		}
	}

	fun apiException(statusCode: Int, body: String): BoxApiException {
		val error = try {
			gson.fromJson(body, BoxError::class.java)
		} catch (e: JsonSyntaxException) {
			null
		}
		return BoxApiException(statusCode, error?.code ?: error?.error, error?.message ?: error?.errorDescription, conflictOf(error))
	}

	/**
	 * Box reports the conflicting item as an object for uploads but as an array for folders.
	 */
	private fun conflictOf(error: BoxError?): BoxItem? {
		val conflicts = error?.contextInfo?.conflicts ?: return null
		val conflict = if (conflicts.isJsonArray) conflicts.asJsonArray.firstOrNull() else conflicts
		return conflict?.takeIf { it.isJsonObject }?.let { gson.fromJson(it, BoxItem::class.java) }
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
