package org.cryptomator.data.cloud.box

import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.exception.FatalBackendException
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class BoxApiTest {

	private val cloud = BoxCloud.aBoxCloud().withId(1L).withUserId("userId").withUsername("user@example.com").build()
	private val responses = CannedResponses()
	private val tokenStore = mock<BoxTokenStore>()
	private val inTest = BoxApi(cloud, tokenStore, OkHttpClient.Builder().addInterceptor(responses).build())

	init {
		whenever(tokenStore.load(cloud)).thenReturn(BoxTokens("accessToken", "refreshToken", Long.MAX_VALUE))
	}

	@ParameterizedTest
	@ValueSource(strings = ["name_temporarily_reserved", "operation_blocked_temporary"])
	fun testTemporaryConflictIsRetried(code: String) {
		responses.add(409, """{"type":"error","status":409,"code":"$code"}""")
		responses.add(201, """{"type":"folder","id":"folderId","name":"folder"}""")

		val folder = inTest.createFolder("0", "folder")

		assertThat(folder.id, `is`("folderId"))
		assertThat(responses.requestCount, `is`(2))
	}

	@Test
	fun testTemporaryConflictKeepsBoxsErrorWhenRetriesRunOut() {
		repeat(10) { responses.add(409, """{"type":"error","status":409,"code":"name_temporarily_reserved"}""") }

		val exception = assertThrows(BoxApiException::class.java) { inTest.createFolder("0", "folder") }

		assertThat(exception.code, `is`("name_temporarily_reserved"))
		assertThat(responses.requestCount, `is`(10))
	}

	@Test
	fun testNameClashIsNotRetried() {
		responses.add(409, """{"type":"error","status":409,"code":"item_name_in_use"}""")

		val exception = assertThrows(BoxApiException::class.java) { inTest.createFolder("0", "folder") }

		assertThat(exception.isConflict, `is`(true))
		assertThat(responses.requestCount, `is`(1))
	}

	@Test
	fun testRateLimitKeepsBoxsErrorWhenRetriesRunOut() {
		repeat(10) { responses.add(429, """{"type":"error","status":429,"code":"rate_limit_exceeded"}""") }

		val exception = assertThrows(BoxApiException::class.java) { inTest.delete(BoxIdCache.NodeInfo("fileId", false)) }

		assertThat(exception.statusCode, `is`(429))
		assertThat(responses.requestCount, `is`(10))
	}

	@Test
	fun testAcceptedOnTheLastAttemptIsASuccess() {
		repeat(9) { responses.add(429, """{"type":"error","status":429,"code":"rate_limit_exceeded"}""") }
		responses.add(202, "")

		inTest.delete(BoxIdCache.NodeInfo("folderId", true))

		assertThat(responses.requestCount, `is`(10))
	}

	@Test
	fun testStillProcessingWhenRetriesRunOutFails() {
		repeat(10) { responses.add(202, "") }

		val exception = assertThrows(FatalBackendException::class.java) { inTest.download("fileId") }

		assertThat(exception is BoxApiException, `is`(false))
		assertThat(responses.requestCount, `is`(10))
	}

	private class CannedResponses : Interceptor {

		private val pending = ArrayDeque<Pair<Int, String>>()
		var requestCount = 0

		fun add(code: Int, body: String) {
			pending.addLast(code to body)
		}

		override fun intercept(chain: Interceptor.Chain): Response {
			requestCount++
			val (code, body) = pending.removeFirst()
			return Response.Builder() //
				.request(chain.request()) //
				.protocol(Protocol.HTTP_1_1) //
				.code(code) //
				.message("") //
				.header("Retry-After", "0") //
				.body(body.toResponseBody("application/json".toMediaType())) //
				.build()
		}
	}
}
