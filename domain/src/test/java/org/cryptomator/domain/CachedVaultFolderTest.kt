package org.cryptomator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CachedVaultFolderTest {

	@Test
	fun `a folder keeps its documentId with another cloud`() {
		val cloud: Cloud = mock()

		val folder = CachedVaultFolder(null, "/a/b", "1:7").withCloud(cloud)

		assertSame(cloud, folder.cloud)
		assertEquals("1:7", folder.documentId)
	}
}
