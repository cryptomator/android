package org.cryptomator.data.util

import org.cryptomator.data.BuildConfig
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class UserAgentInterceptor : Interceptor {

	@Throws(IOException::class)
	override fun intercept(chain: Interceptor.Chain): Response {
		val originalRequest: Request = chain.request()
		val userAgent = "Cryptomator-Android/" + BuildConfig.VERSION_NAME + " " + System.getProperty("http.agent")
		val requestWithUserAgent = originalRequest.newBuilder().header("User-Agent", userAgent).build()
		return chain.proceed(requestWithUserAgent)
	}
}
