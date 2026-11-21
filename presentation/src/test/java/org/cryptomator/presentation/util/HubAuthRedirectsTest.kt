package org.cryptomator.presentation.util

import android.net.Uri
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class HubAuthRedirectsTest {

	private val inTest = HubAuthRedirects()
	private val redirectUri = mock<Uri>()
	private var redirects = 0
	private val onRedirect: () -> Unit = { redirects++ }

	@Test
	fun testRedirectIsHandedOverOnce() {
		inTest.awaitRedirect(onRedirect)

		inTest.receiveRedirect(redirectUri)

		assertThat(redirects, `is`(1))
		assertThat(inTest.takeRedirectUri(), `is`(redirectUri))
		assertThat(inTest.takeRedirectUri(), nullValue())
	}

	@Test
	fun testRedirectNobodyAwaitedIsNotHandedToTheNextLogin() {
		inTest.receiveRedirect(redirectUri)

		inTest.awaitRedirect(onRedirect)

		assertThat(inTest.takeRedirectUri(), nullValue())
	}

	@Test
	fun testTakenRedirectNoLongerNotifies() {
		inTest.awaitRedirect(onRedirect)
		inTest.takeRedirectUri()

		inTest.receiveRedirect(redirectUri)

		assertThat(redirects, `is`(0))
	}

	@Test
	fun testStoppingOnlyRemovesTheOwnCallback() {
		inTest.awaitRedirect(onRedirect)

		inTest.stopAwaitingRedirect {}
		inTest.receiveRedirect(redirectUri)
		assertThat(redirects, `is`(1))

		inTest.stopAwaitingRedirect(onRedirect)
		inTest.receiveRedirect(redirectUri)
		assertThat(redirects, `is`(1))
	}
}
