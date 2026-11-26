package org.cryptomator.domain

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class S3CloudTest {

	@Test
	fun testCopyKeepsTheProvider() {
		val cloud = S3Cloud.aS3Cloud() //
			.withId(42L) //
			.withS3Endpoint("https://s3.eu-central-1.wasabisys.com") //
			.withS3Region("eu-central-1") //
			.withS3Bucket("bucket") //
			.withProvider(S3Provider.WASABI) //
			.build()

		val copy = S3Cloud.aCopyOf(cloud).withDisplayName("Wasabi").build()

		assertThat(copy.provider(), `is`(S3Provider.WASABI))
		assertThat(copy.displayName(), `is`("Wasabi"))
	}
}
