package org.cryptomator.domain

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class SmbCloudTest {

	private val cloud = SmbCloud.aSmbCloud() //
		.withId(42L) //
		.withUrl("smb://nas/share") //
		.withUsername("alice") //
		.withPassword("encryptedPassword") //
		.build()

	@Test
	fun testConfigurationMatchesSameUrlAndUserWithOtherPassword() {
		val samePlace = SmbCloud.aCopyOf(cloud).withId(null).withPassword("otherEncryptedPassword").build()

		assertThat(cloud.configurationMatches(samePlace), `is`(true))
	}

	@Test
	fun testConfigurationDoesNotMatchOtherUserOnSameShare() {
		val otherUser = SmbCloud.aCopyOf(cloud).withId(null).withUsername("bob").build()

		assertThat(cloud.configurationMatches(otherUser), `is`(false))
	}

	@Test
	fun testConfigurationDoesNotMatchOtherShareOfSameUser() {
		val otherShare = SmbCloud.aCopyOf(cloud).withId(null).withUrl("smb://nas/other").build()

		assertThat(cloud.configurationMatches(otherShare), `is`(false))
	}

	@Test
	fun testConfigurationDoesNotMatchOtherCloudType() {
		val webDavCloud = WebDavCloud.aWebDavCloudCloud().withUrl("smb://nas/share").withUsername("alice").build()

		assertThat(cloud.configurationMatches(webDavCloud), `is`(false))
	}
}
