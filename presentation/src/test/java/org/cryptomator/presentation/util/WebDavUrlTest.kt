package org.cryptomator.presentation.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class WebDavUrlTest {

	@ParameterizedTest
	@CsvSource(
		"http://192.168.178.20/webdav, https://192.168.178.20/webdav",
		"http://192.168.178.20:80/webdav, https://192.168.178.20/webdav",
		"http://192.168.178.20:8082/seafdav, https://192.168.178.20:8082/seafdav",
		"http://nas.local, https://nas.local",
		"http://nas.local/my%20cloud/seafdav, https://nas.local/my%20cloud/seafdav",
		"http://nas.local/files[1]/seafdav, https://nas.local/files[1]/seafdav",
		"http://my_nas:8082/seafdav, https://my_nas:8082/seafdav",
		"http://nas.local/dav?share=1, https://nas.local/dav?share=1"
	)
	fun testSwitchedToHttpsKeepsHostPortAndPath(url: String, expected: String) {
		assertEquals(expected, WebDavUrl.switchedToHttps(url))
	}

	@Test
	fun testSwitchedToHttpsKeepsAPathWithAStrayPercentSignReachable() {
		val switched = WebDavUrl.switchedToHttps("http://nas.local/50%off/seafdav").toHttpUrl()

		assertEquals("https", switched.scheme)
		assertEquals("nas.local", switched.host)
		assertEquals(listOf("50%off", "seafdav"), switched.pathSegments)
	}
}
