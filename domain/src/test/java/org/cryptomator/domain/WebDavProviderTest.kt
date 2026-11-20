package org.cryptomator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class WebDavProviderTest {

	@Test
	fun testProviderNamesAreTheStoredValues() {
		assertEquals(
			listOf(
				"ONE_AND_ONE", "DISROOT", "FREENETCLOUD", "GMX", "HIDRIVE_IONOS", "HIDRIVE_STRATO", "ICEDRIVE", "KDRIVE", "KOOFR", "MAGENTACLOUD", "MAILBOX_ORG", "MAIL_RU",
				"NEXTCLOUD", "OPENCLOUD", "OWNCLOUD", "SEAFILE", "STACK", "SWITCHDRIVE", "SYNCWERK", "WEB_DE", "WOELKLI", "YANDEX_DISK"
			),
			WebDavProvider.entries.map { it.name }
		)
	}

	@Test
	fun testFixedUrlIgnoresInput() {
		assertEquals("https://app.koofr.net/dav/Koofr", WebDavProvider.KOOFR.url("", "", null))
		assertEquals("https://drive.switch.ch/remote.php/webdav", WebDavProvider.SWITCHDRIVE.url("", "", null))
		assertFalse(WebDavProvider.KOOFR.requiresServer)
		assertFalse(WebDavProvider.KOOFR.requiresSpaceId)
	}

	@Test
	fun testServerBasedUrlIsAppendedToTheServerUrl() {
		assertEquals("https://cloud.example.org/remote.php/webdav", WebDavProvider.NEXTCLOUD.url("https://cloud.example.org/", "", null))
		assertEquals("https://knowledgebase-4.stack.storage/remote.php/webdav", WebDavProvider.STACK.url("https://knowledgebase-4.stack.storage/", "", null))
		assertEquals("https://cloud.example.org/owncloud/remote.php/webdav", WebDavProvider.OWNCLOUD.url("https://cloud.example.org/owncloud", "", null))
		assertEquals("http://192.168.178.20:8082/seafdav", WebDavProvider.SEAFILE.url("http://192.168.178.20:8082/", "", null))
		assertTrue(WebDavProvider.STACK.requiresServer)
	}

	@Test
	fun testSpaceIdIsInsertedEncoded() {
		assertEquals("https://cloud.example.org/remote.php/dav/spaces/a%24b%2Fc%20d", WebDavProvider.OPENCLOUD.url("https://cloud.example.org/", "a\$b/c d", null))
		assertTrue(WebDavProvider.OPENCLOUD.requiresSpaceId)
	}

	@Test
	fun testVariantSelectsTheUrl() {
		assertEquals("https://sd2dav.1und1.de", WebDavProvider.ONE_AND_ONE.url("", "", WebDavProviderVariant.ONE_AND_ONE_DSL))
		assertEquals("https://webdav.office.1und1.de", WebDavProvider.ONE_AND_ONE.url("", "", WebDavProviderVariant.ONE_AND_ONE_WEBHOSTING))
		assertEquals(listOf(WebDavProviderVariant.ONE_AND_ONE_DSL, WebDavProviderVariant.ONE_AND_ONE_WEBHOSTING), WebDavProvider.ONE_AND_ONE.variants)
		assertTrue(WebDavProvider.KOOFR.variants.isEmpty())
	}

	@Test
	fun testVariantOfAnotherProviderIsRejected() {
		assertThrows(IllegalArgumentException::class.java) { WebDavProvider.KOOFR.url("", "", WebDavProviderVariant.ONE_AND_ONE_DSL) }
	}

	@ParameterizedTest
	@EnumSource(WebDavProvider::class)
	fun testNoPlaceholderIsLeftInUrl(provider: WebDavProvider) {
		val url = provider.url("https://cloud.example.org/", "space", null)

		assertFalse(url.contains("{"), url)
		assertFalse(url.endsWith("/"), url)
	}
}
