package org.cryptomator.presentation.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingCallbackQueueTest {

	private val inTest = PendingCallbackQueue<String>()
	private val received = mutableListOf<String>()

	private fun callback(name: String): (String) -> Unit = { value -> received.add("$name:$value") }

	@Test
	fun `markReadyAndDrain returns callbacks enqueued before ready only once`() {
		assertTrue(inTest.enqueueUnlessReady(callback("first")))
		assertTrue(inTest.enqueueUnlessReady(callback("second")))

		inTest.markReadyAndDrain()!!.forEach { it("value") }

		assertEquals(listOf("first:value", "second:value"), received)
		assertTrue(inTest.isReady)
		assertNull(inTest.markReadyAndDrain())
	}

	@Test
	fun `markReadyAndDrain returns null without pending callbacks`() {
		assertNull(inTest.markReadyAndDrain())
		assertTrue(inTest.isReady)
	}

	@Test
	fun `enqueueUnlessReady rejects callbacks once ready`() {
		inTest.markReadyAndDrain()

		assertFalse(inTest.enqueueUnlessReady(callback("late")))
		assertNull(inTest.markReadyAndDrain())
	}

	@Test
	fun `markNotReady holds callbacks back again until next markReadyAndDrain`() {
		inTest.markReadyAndDrain()
		inTest.markNotReady()

		assertFalse(inTest.isReady)
		assertTrue(inTest.enqueueUnlessReady(callback("reconnect")))
		inTest.markReadyAndDrain()!!.forEach { it("value") }

		assertEquals(listOf("reconnect:value"), received)
	}
}
