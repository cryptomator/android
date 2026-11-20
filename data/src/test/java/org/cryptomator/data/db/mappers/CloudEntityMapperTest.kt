package org.cryptomator.data.db.mappers

import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.SharepointCloud
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.WebDavProvider
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class CloudEntityMapperTest {

	private val mapper = CloudEntityMapper()

	@Test
	fun testSharepointCloudSurvivesRoundTrip() {
		val cloud = SharepointCloud.aSharepointCloud() //
			.withId(42L) //
			.withAccessToken("accessToken") //
			.withUsername("user@contoso.com") //
			.withSiteUrl("https://contoso.sharepoint.com/sites/team") //
			.withDriveId("driveId") //
			.withDriveName("Documents") //
			.build()

		val entity = mapper.toEntity(cloud)
		val mapped = mapper.fromEntity(entity) as SharepointCloud

		assertThat(entity.type, `is`(CloudType.SHAREPOINT.name))
		assertThat(mapped.id(), `is`(42L))
		assertThat(mapped.accessToken(), `is`("accessToken"))
		assertThat(mapped.username(), `is`("user@contoso.com"))
		assertThat(mapped.siteUrl(), `is`("https://contoso.sharepoint.com/sites/team"))
		assertThat(mapped.driveId(), `is`("driveId"))
		assertThat(mapped.driveName(), `is`("Documents"))
	}

	@Test
	fun testBoxCloudSurvivesRoundTripWithoutTokens() {
		val cloud = BoxCloud.aBoxCloud() //
			.withId(42L) //
			.withUserId("userId") //
			.withUsername("user@example.com") //
			.build()

		val entity = mapper.toEntity(cloud)
		val mapped = mapper.fromEntity(entity) as BoxCloud

		assertThat(entity.type, `is`(CloudType.BOX.name))
		assertThat(entity.accessToken, `is`(nullValue()))
		assertThat(mapped.id(), `is`(42L))
		assertThat(mapped.userId(), `is`("userId"))
		assertThat(mapped.username(), `is`("user@example.com"))
	}

	@Test
	fun testWebDavProviderSurvivesRoundTrip() {
		val cloud = WebDavCloud.aWebDavCloudCloud() //
			.withId(42L) //
			.withUrl("https://app.koofr.net/dav/Koofr") //
			.withUsername("user@example.com") //
			.withPassword("password") //
			.withProvider(WebDavProvider.KOOFR) //
			.build()

		val entity = mapper.toEntity(cloud)
		val mapped = mapper.fromEntity(entity) as WebDavCloud

		assertThat(entity.type, `is`(CloudType.WEBDAV.name))
		assertThat(entity.webdavProvider, `is`("KOOFR"))
		assertThat(mapped.provider(), `is`(WebDavProvider.KOOFR))
	}

	@Test
	fun testWebDavCloudWithoutProviderSurvivesRoundTrip() {
		val cloud = WebDavCloud.aWebDavCloudCloud() //
			.withId(42L) //
			.withUrl("https://example.org/webdav") //
			.withUsername("user") //
			.withPassword("password") //
			.build()

		val entity = mapper.toEntity(cloud)
		val mapped = mapper.fromEntity(entity) as WebDavCloud

		assertThat(entity.webdavProvider, `is`(nullValue()))
		assertThat(mapped.provider(), `is`(nullValue()))
	}
}
