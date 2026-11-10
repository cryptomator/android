package org.cryptomator.domain

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class WebDavCloudTest {

	@Test
	fun testCopyKeepsTheProvider() {
		val cloud = WebDavCloud.aWebDavCloudCloud() //
			.withId(42L) //
			.withUrl("https://app.koofr.net/dav/Koofr") //
			.withUsername("alice") //
			.withPassword("password") //
			.withProvider(WebDavProvider.KOOFR) //
			.build()

		val copy = WebDavCloud.aCopyOf(cloud).withCertificate("certificate").build()

		assertThat(copy.provider(), `is`(WebDavProvider.KOOFR))
		assertThat(copy.certificate(), `is`("certificate"))
	}
}
