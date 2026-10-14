package org.cryptomator.presentation.docprovider

import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VaultPathTest {

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).build()
	private val otherVault = Vault.aVault().withId(2L).withName("other").withPath("/other").withCloudType(CloudType.LOCAL).withPosition(1).build()

	@Test
	fun `a descendant is a child of its ancestors`() {
		assertTrue(VaultPath(vault, "a/b/c.txt").isAnyChildOf(VaultPath(vault, "a/b")))
		assertTrue(VaultPath(vault, "a/b/c.txt").isAnyChildOf(VaultPath(vault, "a")))
		assertTrue(VaultPath(vault, "a/b/c.txt").isAnyChildOf(VaultPath(vault)))
	}

	@Test
	fun `a sibling whose name starts with the folder's name is no child of it`() {
		assertFalse(VaultPath(vault, "ab/secret.txt").isAnyChildOf(VaultPath(vault, "a")))
		assertFalse(VaultPath(vault, "ab").isAnyChildOf(VaultPath(vault, "a")))
	}

	@Test
	fun `a node is no child of itself or of its descendants`() {
		assertFalse(VaultPath(vault, "a").isAnyChildOf(VaultPath(vault, "a")))
		assertFalse(VaultPath(vault, "a").isAnyChildOf(VaultPath(vault, "a/b")))
		assertFalse(VaultPath(vault).isAnyChildOf(VaultPath(vault)))
	}

	@Test
	fun `nodes of another vault are no children`() {
		assertFalse(VaultPath(otherVault, "a/b").isAnyChildOf(VaultPath(vault, "a")))
		assertFalse(VaultPath(otherVault, "a").isAnyChildOf(VaultPath(vault)))
	}

	@Test
	fun `paths are normalized and compared by vault and path`() {
		assertEquals(VaultPath(vault, "a/b"), VaultPath(vault, "/a/b/"))
		assertEquals(VaultPath(vault, "a/b").hashCode(), VaultPath(vault, "/a/b/").hashCode())
		assertNotEquals(VaultPath(vault, "a"), VaultPath(otherVault, "a"))
		assertEquals(VaultPath(vault, "a"), VaultPath(vault, "a/b").parent)
		assertEquals(VaultPath(vault), VaultPath(vault, "a").parent)
		assertEquals(VaultPath(vault, "a/b"), VaultPath(vault, "a").resolve("b"))
	}
}
