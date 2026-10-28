package org.cryptomator.data.cloud.crypto

import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class CryptorsTest {

	private val inTest = Cryptors.Default()
	private var changes = 0
	private val first = vault(1)
	private val second = vault(2)

	init {
		inTest.setOnChangeListener { changes++ }
	}

	@Test
	fun `locking all vaults tells the listener once, locking with none unlocked doesn't`() {
		val cryptor: Cryptor = mock()
		inTest.putIfAbsent(first, cryptor)
		inTest.putIfAbsent(second, mock())
		changes = 0

		inTest.destroyAll()

		assertEquals(1, changes)
		assertTrue(inTest.isEmpty())
		verify(cryptor).destroy()

		inTest.destroyAll()

		assertEquals(1, changes)
	}

	@Test
	fun `adding vaults tells the listener, adding none or only known ones doesn't`() {
		val cryptor: Cryptor = mock()

		inTest.putAll(emptyMap())
		assertEquals(0, changes)

		inTest.putAll(mapOf(first to cryptor))
		assertEquals(1, changes)

		inTest.putAll(mapOf(first to cryptor))
		assertEquals(1, changes)
	}

	@Test
	fun `a vault is found as it was unlocked, e g with what its Hub unlock found out, and not once it is locked`() {
		val unlocked = Vault.aCopyOf(first).withHubVault(true).withHubPaidLicense(true).build()
		inTest.putIfAbsent(unlocked, mock())

		assertSame(unlocked, inTest.unlockedVault(first))
		assertNull(inTest.unlockedVault(second))
		inTest.remove(first)
		assertNull(inTest.unlockedVault(first))
	}

	private fun vault(id: Long): Vault {
		return Vault.aVault().withId(id).withName("vault$id").withPath("/vault$id").withCloudType(CloudType.LOCAL).withPosition(id.toInt()).build()
	}
}
