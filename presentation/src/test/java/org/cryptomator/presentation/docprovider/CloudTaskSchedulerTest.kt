package org.cryptomator.presentation.docprovider

import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

@Timeout(10)
class CloudTaskSchedulerTest {

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).build()
	private val otherVault = Vault.aVault().withId(2L).withName("other").withPath("/other").withCloudType(CloudType.LOCAL).withPosition(1).build()
	private val inTest = CloudTaskScheduler()

	@Test
	fun `a write waits for an earlier read of its path`() {
		assertWaits({ inTest.download(path("a/x.txt"), it) }, { inTest.write(listOf(path("a/x.txt")), it) })
	}

	@Test
	fun `a read waits for an earlier write of its path`() {
		assertWaits({ inTest.write(listOf(path("a/x.txt")), it) }, { inTest.download(path("a/x.txt"), it) })
	}

	@Test
	fun `a request waits for an earlier write of an ancestor`() {
		assertWaits({ inTest.write(listOf(path("a"), path("b"))) { it() } }, { inTest.download(path("b/x.txt"), it) })
	}

	@Test
	fun `a write of a folder waits for an earlier upload below it`() {
		assertWaits({ inTest.upload(path("a/b/x.txt"), it) }, { inTest.write(listOf(path("a"))) { it() } })
	}

	@Test
	fun `reads of the same path run in parallel`() {
		assertParallel({ inTest.download(path("a/x.txt"), it) }, { inTest.read(path("a/x.txt"), it) })
	}

	@Test
	fun `a listing runs while an upload into the folder is running`() {
		assertParallel({ inTest.upload(path("a/x.txt"), it) }, { inTest.read(path("a"), it) })
	}

	@Test
	fun `changes of unrelated paths run in parallel`() {
		assertParallel({ inTest.upload(path("a/x.txt"), it) }, { inTest.write(listOf(path("b/y.txt"))) { it() } })
	}

	@Test
	fun `the same path in another vault is unrelated`() {
		assertParallel({ inTest.write(listOf(path("a"))) { it() } }, { inTest.write(listOf(VaultPath(otherVault, "a"))) { it() } })
	}

	@Test
	fun `a failed request does not block later ones`() {
		val failed = inTest.write(listOf(path("a"))) { throw IllegalStateException("failed") }

		val later = inTest.write(listOf(path("a"))) { "done" }

		assertEquals("done", later.get(5, TimeUnit.SECONDS))
		assertTrue(failed.isCompletedExceptionally)
	}

	@Test
	fun `a request keeps the order of requests made before it, not after it`() {
		val events = mutableListOf<String>()
		val firstStarted = CountDownLatch(1)
		val release = CountDownLatch(1)
		val first = inTest.write(listOf(path("a"))) {
			firstStarted.countDown()
			release.await()
			synchronized(events) { events.add("first") }
		}
		firstStarted.await()
		val second = inTest.read(path("a")) { synchronized(events) { events.add("second") } }
		val third = inTest.write(listOf(path("a"))) { synchronized(events) { events.add("third") } }

		release.countDown()
		listOf(first, second, third).forEach { it.get(5, TimeUnit.SECONDS) }

		assertEquals(listOf("first", "second", "third"), events)
	}

	/**
	 * [first] holds its slot until released; [second] must not start before that.
	 */
	private fun assertWaits(first: (() -> Unit) -> Any, second: (() -> Unit) -> Any) {
		val firstStarted = CountDownLatch(1)
		val release = CountDownLatch(1)
		val secondStarted = CountDownLatch(1)
		first {
			firstStarted.countDown()
			release.await()
		}
		assertTrue(firstStarted.await(5, TimeUnit.SECONDS))

		second { secondStarted.countDown() }

		assertFalse(secondStarted.await(200, TimeUnit.MILLISECONDS))
		release.countDown()
		assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
	}

	/**
	 * Both requests only finish if they run at the same time.
	 */
	private fun assertParallel(first: (() -> Unit) -> Any, second: (() -> Unit) -> Any) {
		val barrier = CyclicBarrier(2)
		val bothRan = CountDownLatch(2)
		first {
			barrier.await(5, TimeUnit.SECONDS)
			bothRan.countDown()
		}
		second {
			barrier.await(5, TimeUnit.SECONDS)
			bothRan.countDown()
		}

		assertTrue(bothRan.await(5, TimeUnit.SECONDS))
	}

	private fun path(path: String): VaultPath {
		return VaultPath(vault, path)
	}
}
