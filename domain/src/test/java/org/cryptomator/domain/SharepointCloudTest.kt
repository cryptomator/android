package org.cryptomator.domain

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class SharepointCloudTest {

	private val cloud = SharepointCloud.aSharepointCloud() //
		.withId(42L) //
		.withAccessToken("accessToken") //
		.withUsername("user@contoso.com") //
		.withSiteUrl("https://contoso.sharepoint.com/sites/team") //
		.withDriveId("driveId") //
		.withDriveName("Documents") //
		.build()

	@Test
	fun testCopyKeepsAllFields() {
		val copy = SharepointCloud.aCopyOf(cloud).build()

		assertThat(copy.id(), `is`(42L))
		assertThat(copy.accessToken(), `is`("accessToken"))
		assertThat(copy.username(), `is`("user@contoso.com"))
		assertThat(copy.siteUrl(), `is`("https://contoso.sharepoint.com/sites/team"))
		assertThat(copy.driveId(), `is`("driveId"))
		assertThat(copy.driveName(), `is`("Documents"))
	}

	@Test
	fun testConfigurationMatchesSameUserAndDrive() {
		val sameDrive = SharepointCloud.aSharepointCloud() //
			.withUsername("user@contoso.com") //
			.withDriveId("driveId") //
			.build()

		assertThat(cloud.configurationMatches(sameDrive), `is`(true))
	}

	@Test
	fun testConfigurationDoesNotMatchOtherDriveOfSameUser() {
		val otherDrive = SharepointCloud.aCopyOf(cloud).withId(null).withDriveId("otherDriveId").build()

		assertThat(cloud.configurationMatches(otherDrive), `is`(false))
	}

	@Test
	fun testConfigurationDoesNotMatchSameDriveOfOtherUser() {
		val otherUser = SharepointCloud.aCopyOf(cloud).withId(null).withUsername("other@contoso.com").build()

		assertThat(cloud.configurationMatches(otherUser), `is`(false))
	}

	@Test
	fun testConfigurationDoesNotMatchOnedriveCloudOfSameUser() {
		val onedriveCloud = OnedriveCloud.aOnedriveCloud().withUsername("user@contoso.com").build()

		assertThat(cloud.configurationMatches(onedriveCloud), `is`(false))
	}
}
