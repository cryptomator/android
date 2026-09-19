package org.cryptomator.data.cloud.webdav.network

import android.content.Context
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.nio.charset.StandardCharsets
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okhttp3.Request

class DataSourceBasedRequestBodyRetryTest {

	private val context = mock<Context>()

	private lateinit var server: MockWebServer

	@BeforeEach
	fun setup() {
		server = MockWebServer()
		server.start()
	}

	@AfterEach
	fun tearDown() {
		server.close()
	}

	/**
	 * Reproduces the upload failing after the server closed the connection it was pooled on, e.g. because of the
	 * keep-alive timeout of Apache, which defaults to five seconds. Opening a text file downloads it and leaves the
	 * connection in the pool, saving it reuses that connection. OkHttp repeats such a request using a new connection,
	 * which only succeeds if the request body writes the complete content again.
	 * see https://github.com/cryptomator/android/issues/646
	 */
	@Test
	@DisplayName("upload is repeated with the complete content after the server closed the pooled connection")
	fun testUploadIsRepeatedWithTheCompleteContentAfterTheServerClosedThePooledConnection() {
		server.enqueue(MockResponse.Builder().code(200).body("Wer die Wahl hat").build())
		server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket(true, true, true)).build())
		server.enqueue(MockResponse.Builder().code(204).build())

		val url = server.url("/vault/d/AB/CDEFGH.c9r")
		val client = OkHttpClient()

		client.newCall(Request.Builder().url(url).build()).execute().use { response ->
			MatcherAssert.assertThat(response.code, CoreMatchers.`is`(200))
		}

		val requestBody = DataSourceBasedRequestBody.from(context, ByteArrayDataSource.from(CONTENT), CONTENT.size.toLong()) { it }
		val request = Request.Builder() //
			.put(requestBody) //
			.url(url) //
			.build()

		client.newCall(request).execute().use { response ->
			MatcherAssert.assertThat(response.code, CoreMatchers.`is`(204))
		}

		val uploads = recordedRequests().filter { it.method == "PUT" }
		MatcherAssert.assertThat(uploads.size, CoreMatchers.`is`(2))
		MatcherAssert.assertThat(uploads.last().body?.toByteArray(), CoreMatchers.`is`(CONTENT))
		MatcherAssert.assertThat(uploads.last().connectionIndex, CoreMatchers.not(uploads.first().connectionIndex))
	}

	private fun recordedRequests(): List<RecordedRequest> {
		return (0 until server.requestCount).map { server.takeRequest() }
	}

	companion object {

		private val CONTENT = "Wer die Wahl hat, hat die Qual".toByteArray(StandardCharsets.UTF_8)

	}
}
