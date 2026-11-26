package org.cryptomator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class S3ProviderTest {

	@Test
	fun testProviderNamesAreTheStoredValues() {
		assertEquals(
			listOf(
				"AKAMAI", "AMAZON_S3", "BACKBLAZE_B2", "CLOUDFLARE_R2", "DIGITALOCEAN_SPACES", "EXOSCALE", "GOOGLE_CLOUD_STORAGE", "HETZNER", "IONOS", "MINIO", "OVHCLOUD",
				"SCALEWAY", "STORJ", "VULTR", "WASABI"
			),
			S3Provider.entries.map { it.name }
		)
	}

	@Test
	fun testChosenRegionIsInsertedIntoTheEndpointAndUsedAsRegion() {
		assertEquals("https://s3.eu-central-1.wasabisys.com", S3Provider.WASABI.endpoint("", "", "eu-central-1"))
		assertEquals("https://sos-ch-gva-2.exo.io", S3Provider.EXOSCALE.endpoint("", "", "ch-gva-2"))
		assertEquals("eu-central-1", S3Provider.WASABI.region("eu-central-1"))
		assertFalse(S3Provider.WASABI.requiresServer)
		assertFalse(S3Provider.WASABI.requiresAccountId)
		assertFalse(S3Provider.WASABI.choiceIsEndpointName)
	}

	@Test
	fun testIonosRegionOfFrankfurtIsNamedDe() {
		assertEquals("https://s3.eu-central-1.ionoscloud.com", S3Provider.IONOS.endpoint("", "", "eu-central-1"))
		assertEquals("de", S3Provider.IONOS.region("eu-central-1"))
		assertEquals("eu-central-3", S3Provider.IONOS.region("eu-central-3"))
	}

	@Test
	fun testChosenEndpointNameIsInsertedIntoTheEndpointWithAFixedRegion() {
		assertEquals("https://us-ord-10.linodeobjects.com", S3Provider.AKAMAI.endpoint("", "", "us-ord-10"))
		assertEquals("https://ewr1.vultrobjects.com", S3Provider.VULTR.endpoint("", "", "ewr1"))
		assertEquals("us-east-1", S3Provider.VULTR.region("ewr1"))
		assertTrue(S3Provider.VULTR.choiceIsEndpointName)
		assertTrue(S3Provider.AKAMAI.choiceIsEndpointName)
	}

	@Test
	fun testFixedEndpointAndRegionIgnoreInput() {
		assertEquals("https://gateway.storjshare.io", S3Provider.STORJ.endpoint("https://s3.example.org", "account", "eu-central-1"))
		assertEquals("us-east-1", S3Provider.STORJ.region("eu-central-1"))
		assertTrue(S3Provider.STORJ.choices.isEmpty())
		assertFalse(S3Provider.STORJ.choiceIsEndpointName)
		assertFalse(S3Provider.STORJ.requiresServer)
		assertFalse(S3Provider.STORJ.requiresAccountId)
	}

	@Test
	fun testAccountIdIsInsertedIntoTheEndpoint() {
		assertEquals("https://0123456789abcdef.r2.cloudflarestorage.com", S3Provider.CLOUDFLARE_R2.endpoint("", "0123456789abcdef", ""))
		assertEquals("auto", S3Provider.CLOUDFLARE_R2.region(""))
		assertTrue(S3Provider.CLOUDFLARE_R2.requiresAccountId)
		assertTrue(S3Provider.CLOUDFLARE_R2.choices.isEmpty())
	}

	@Test
	fun testServerBasedEndpointIsTheServerUrlAndTheChosenRegionIsUsedAsRegion() {
		assertEquals("http://192.168.178.20:9000", S3Provider.MINIO.endpoint("http://192.168.178.20:9000/", "", "us-east-1"))
		assertEquals("eu-west-1", S3Provider.MINIO.region("eu-west-1"))
		assertTrue(S3Provider.MINIO.requiresServer)
		assertFalse(S3Provider.MINIO.choiceIsEndpointName)
		assertEquals(listOf("us-east-1"), S3Provider.MINIO.choices)
	}

	@ParameterizedTest
	@EnumSource(S3Provider::class)
	fun testChoicesAreDistinct(provider: S3Provider) {
		assertEquals(provider.choices.distinct(), provider.choices)
	}

	@ParameterizedTest
	@EnumSource(S3Provider::class)
	fun testNoPlaceholderIsLeftInEndpoint(provider: S3Provider) {
		val endpoint = provider.endpoint("https://s3.example.org/", "account", "choice")

		assertFalse(endpoint.contains("{"), endpoint)
		assertFalse(endpoint.endsWith("/"), endpoint)
	}
}
