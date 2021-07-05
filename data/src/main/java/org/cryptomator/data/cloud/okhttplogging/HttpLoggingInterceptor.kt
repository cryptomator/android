package org.cryptomator.data.cloud.okhttplogging

import android.content.Context
import org.cryptomator.util.SharedPreferencesHandler
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

class HttpLoggingInterceptor(private val logger: Logger, private val context: Context) : Interceptor {

	@Throws(IOException::class)
	override fun intercept(chain: Interceptor.Chain): Response {
		return if (debugModeEnabled(context)) {
			proceedWithLogging(chain)
		} else {
			chain.proceed(chain.request())
		}
	}

	@Throws(IOException::class)
	private fun proceedWithLogging(chain: Interceptor.Chain): Response {
		val request: Request = chain.request()
		logRequest(request, chain)
		return getAndLogResponse(request, chain)
	}

	@Throws(IOException::class)
	private fun logRequest(request: Request, chain: Interceptor.Chain) {
		logRequestStart(request, chain)
		logContentTypeAndLength(request)
		logHeaders(request.headers)
		logRequestEnd(request)
	}

	@Throws(IOException::class)
	private fun getAndLogResponse(request: Request, chain: Interceptor.Chain): Response {
		val startOfRequestMs = System.nanoTime()
		val response = getResponseLoggingExceptions(request, chain)
		val requestDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startOfRequestMs)
		logResponse(response, requestDurationMs)
		return response
	}

	@Throws(IOException::class)
	private fun getResponseLoggingExceptions(request: Request, chain: Interceptor.Chain): Response {
		return try {
			chain.proceed(request)
		} catch (e: Exception) {
			logger.log("<-- HTTP FAILED: $e")
			throw e
		}
	}

	private fun logResponse(response: Response, requestDurationMs: Long) {
		logResponseStart(response, requestDurationMs)
		logHeaders(response.headers)
		logger.log("<-- END HTTP")
	}

	@Throws(IOException::class)
	private fun logRequestStart(request: Request, chain: Interceptor.Chain) {
		val connection = chain.connection()
		val protocol = connection?.protocol() ?: Protocol.HTTP_1_1
		val bodyLength = if (hasBody(request)) request.body?.contentLength().toString() + "-byte body" else "unknown length"
		logger.log(String.format("--> %s %s %s (%s)", request.method, request.url, protocol, bodyLength))
	}

	@Throws(IOException::class)
	private fun logContentTypeAndLength(request: Request) {
		// Request body headers are only present when installed as a network interceptor. Force
		// them to be included (when available) so there values are known.
		if (hasBody(request)) {
			val body = request.body
			if (body?.contentType() != null) {
				logger.log("Content-Type: " + body.contentType())
			}
			if (body?.contentLength() != -1L) {
				logger.log("Content-Length: " + body?.contentLength())
			}
		}
	}

	private fun logRequestEnd(request: Request) {
		logger.log("--> END " + request.method)
	}

	private fun logResponseStart(response: Response, requestDurationMs: Long) {
		logger.log("<-- " + response.code + ' ' + response.message + ' ' + response.request.url + " (" + requestDurationMs + "ms" + ')')
	}

	private fun hasBody(request: Request): Boolean {
		return request.body != null
	}

	private fun logHeaders(headers: Headers) {
		var i = 0
		while (i < headers.size) {
			val name = headers.name(i)
			if (isExcludedHeader(name)) {
				i++
				continue
			}
			logger.log(name + ": " + headers.value(i))
			i++
		}
	}

	private fun isExcludedHeader(name: String): Boolean {
		return EXCLUDED_HEADERS.contains(name)
	}

	interface Logger {
		fun log(message: String)
	}

	companion object {

		private val EXCLUDED_HEADERS = HeaderNames( //
			// headers excluded because they are logged separately:
			"Content-Type", "Content-Length",  // headers excluded because they contain sensitive information:
			"Authorization",  //
			"WWW-Authenticate",  //
			"Cookie",  //
			"Set-Cookie" //
		)

		private fun debugModeEnabled(context: Context): Boolean {
			return SharedPreferencesHandler(context).debugMode()
		}
	}
}
