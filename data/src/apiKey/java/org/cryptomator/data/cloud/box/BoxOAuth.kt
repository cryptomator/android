package org.cryptomator.data.cloud.box

import org.cryptomator.data.BuildConfig
import java.io.IOException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

internal class BoxOAuth(private val httpClient: OkHttpClient) {

	@Throws(IOException::class)
	fun exchange(authorizationCode: String): BoxTokens {
		return requestTokens(
			FormBody.Builder() //
				.add("grant_type", "authorization_code") //
				.add("code", authorizationCode)
		)
	}

	/**
	 * Box rotates refresh tokens, so the returned tokens have to be persisted before the old refresh token is used again.
	 */
	@Throws(IOException::class)
	fun refresh(refreshToken: String): BoxTokens {
		return requestTokens(
			FormBody.Builder() //
				.add("grant_type", "refresh_token") //
				.add("refresh_token", refreshToken)
		)
	}

	private fun requestTokens(form: FormBody.Builder): BoxTokens {
		val body = form //
			.add("client_id", BuildConfig.BOX_CLIENT_ID) //
			.add("client_secret", BuildConfig.BOX_CLIENT_SECRET) //
			.build()
		val request = Request.Builder().url(TOKEN_URL).post(body).build()
		val response = BoxHttpClient.parse(httpClient.newCall(request).execute(), BoxTokenResponse::class.java)
		return BoxTokens(response.accessToken, response.refreshToken, System.currentTimeMillis() + response.expiresIn * 1000)
	}

	companion object {

		private const val TOKEN_URL = "https://api.box.com/oauth2/token"
	}
}
