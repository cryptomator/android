package org.cryptomator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class WebDavProviderTest {

	@Test
	fun testProviderNamesAreTheStoredValues() {
		assertEquals(
			listOf(
				"ONE_AND_ONE_DSL", "ONE_AND_ONE_WEBHOSTING", "DISROOT", "FREENETCLOUD", "GMX", "HIDRIVE_IONOS", "HIDRIVE_STRATO", "ICEDRIVE", "KDRIVE", "KOOFR", "MAGENTACLOUD", "MAILBOX_ORG", "MAIL_RU",
				"NEXTCLOUD", "OPENCLOUD", "OWNCLOUD", "PCLOUD_EU", "PCLOUD_US", "SEAFILE", "STACK", "SWITCHDRIVE", "SYNCWERK", "WEB_DE", "WOELKLI", "YANDEX_DISK"
			),
			WebDavProvider.entries.map { it.name }
		)
	}

	@Test
	fun testFixedUrlIgnoresInput() {
		assertEquals("https://app.koofr.net/dav/Koofr", WebDavProvider.KOOFR.url("", ""))
		assertEquals("https://drive.switch.ch/remote.php/webdav", WebDavProvider.SWITCHDRIVE.url("", ""))
		assertFalse(WebDavProvider.KOOFR.requiresServer)
		assertFalse(WebDavProvider.KOOFR.requiresSpaceId)
	}

	@Test
	fun testServerBasedUrlIsAppendedToTheServerUrl() {
		assertEquals("https://cloud.example.org/remote.php/webdav", WebDavProvider.NEXTCLOUD.url("https://cloud.example.org/", ""))
		assertEquals("https://knowledgebase-4.stack.storage/remote.php/webdav", WebDavProvider.STACK.url("https://knowledgebase-4.stack.storage/", ""))
		assertEquals("https://cloud.example.org/owncloud/remote.php/webdav", WebDavProvider.OWNCLOUD.url("https://cloud.example.org/owncloud", ""))
		assertEquals("http://192.168.178.20:8082/seafdav", WebDavProvider.SEAFILE.url("http://192.168.178.20:8082/", ""))
		assertTrue(WebDavProvider.STACK.requiresServer)
	}

	@Test
	fun testSpaceIdIsInsertedEncoded() {
		assertEquals("https://cloud.example.org/remote.php/dav/spaces/a%24b%2Fc%20d", WebDavProvider.OPENCLOUD.url("https://cloud.example.org/", "a\$b/c d"))
		assertTrue(WebDavProvider.OPENCLOUD.requiresSpaceId)
	}

	@ParameterizedTest
	@EnumSource(WebDavProvider::class)
	fun testNoPlaceholderIsLeftInUrl(provider: WebDavProvider) {
		val url = provider.url("https://cloud.example.org/", "space")

		assertFalse(url.contains("{"), url)
		assertFalse(url.endsWith("/"), url)
	}
}
