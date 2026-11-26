package org.cryptomator.data.db.mappers

import org.cryptomator.domain.BoxCloud
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.S3Provider
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
	fun testS3ProviderSurvivesRoundTrip() {
		val cloud = S3Cloud.aS3Cloud() //
			.withId(42L) //
			.withS3Endpoint("https://s3.eu-central-1.wasabisys.com") //
			.withS3Region("eu-central-1") //
			.withS3Bucket("bucket") //
			.withAccessKey("accessKey") //
			.withSecretKey("secretKey") //
			.withDisplayName("Wasabi") //
			.withProvider(S3Provider.WASABI) //
			.build()

		val entity = mapper.toEntity(cloud)
		val mapped = mapper.fromEntity(entity) as S3Cloud

		assertThat(entity.type, `is`(CloudType.S3.name))
		assertThat(entity.s3Provider, `is`("WASABI"))
		assertThat(mapped.provider(), `is`(S3Provider.WASABI))
	}

	@Test
	fun testS3CloudWithoutProviderSurvivesRoundTrip() {
		val cloud = S3Cloud.aS3Cloud() //
			.withId(42L) //
			.withS3Endpoint("https://s3.example.org") //
			.withS3Region("region") //
			.withS3Bucket("bucket") //
			.build()

		val entity = mapper.toEntity(cloud)
		val mapped = mapper.fromEntity(entity) as S3Cloud

		assertThat(entity.s3Provider, `is`(nullValue()))
		assertThat(mapped.provider(), `is`(nullValue()))
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
