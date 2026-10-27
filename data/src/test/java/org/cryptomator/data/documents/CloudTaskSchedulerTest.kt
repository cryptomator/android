package org.cryptomator.data.documents

import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.MissingCryptorException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@Timeout(10)
class CloudTaskSchedulerTest {

	private val vault = Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).build()
	private val otherVault = Vault.aVault().withId(2L).withName("other").withPath("/other").withCloudType(CloudType.LOCAL).withPosition(1).build()
	private val inTest = CloudTaskScheduler()
	private val direct = Executor { it.run() }

	@Test
	fun `a write waits for an earlier read of its path`() {
		assertWaits({ inTest.download(path("a/x.txt"), it) }, { write(listOf(path("a/x.txt")), it) })
	}

	@Test
	fun `a read waits for an earlier write of its path`() {
		assertWaits({ write(listOf(path("a/x.txt")), it) }, { inTest.download(path("a/x.txt"), it) })
	}

	@Test
	fun `a request waits for an earlier write of an ancestor`() {
		assertWaits({ write(listOf(path("a"), path("b"))) { it() } }, { inTest.download(path("b/x.txt"), it) })
	}

	@Test
	fun `a write of a folder waits for an earlier upload below it`() {
		assertWaits({ inTest.upload(path("a/b/x.txt"), it) }, { write(listOf(path("a"))) { it() } })
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
		assertParallel({ inTest.upload(path("a/x.txt"), it) }, { write(listOf(path("b/y.txt"))) { it() } })
	}

	@Test
	fun `the same path in another vault is unrelated`() {
		assertParallel({ write(listOf(path("a"))) { it() } }, { write(listOf(VaultPath(otherVault, "a"))) { it() } })
	}

	@Test
	fun `a failed request does not block later ones`() {
		val failed = write(listOf(path("a"))) { throw IllegalStateException("failed") }

		val later = write(listOf(path("a"))) { "done" }

		assertEquals("done", later.get(5, TimeUnit.SECONDS))
		assertTrue(failed.isCompletedExceptionally)
	}

	@Test
	fun `a write holds its paths until the future its task returns is done`() {
		val change = CompletableFuture<String>()
		val first = inTest.write(listOf(path("a"))) { change }
		val laterRead = inTest.download(path("a/x.txt")) { "read" }
		val laterWrite = inTest.upload(path("a/y.txt")) { "written" }

		assertThrows<TimeoutException> { laterRead.get(200, TimeUnit.MILLISECONDS) }
		assertFalse(laterWrite.isDone)
		change.complete("first")

		assertEquals("read", laterRead.get(5, TimeUnit.SECONDS))
		assertEquals("written", laterWrite.get(5, TimeUnit.SECONDS))
		assertEquals("first", first.get(5, TimeUnit.SECONDS))
	}

	@Test
	fun `a read of a locked vault fails right away rather than wait`() {
		inTest.isLocked = { it == vault }
		inTest.write(listOf(path("a"))) { CompletableFuture<String>() }
		var ran = false

		val read = inTest.read(path("a/x.txt")) { ran = true }

		assertTrue(read.isCompletedExceptionally)
		assertThrows<ExecutionException> { read.get() }.also { assertInstanceOf(MissingCryptorException::class.java, it.cause) }
		assertFalse(ran)
		assertEquals("other", inTest.read(VaultPath(otherVault, "a")) { "other" }.get(5, TimeUnit.SECONDS))
	}

	@Test
	fun `the waiting reads of a vault fail once a write finds it locked, and later ones wait again after the unlock`() {
		val inTest = CloudTaskScheduler(downloadExecutor = direct)
		var locked = false
		inTest.isLocked = { locked && it == vault }
		val change = CompletableFuture<String>()
		inTest.write(listOf(path("a"))) { change }
		var waitingReadRan = false
		val waitingRead = inTest.download(path("a/x.txt")) { waitingReadRan = true }
		var failedUnderLock: Boolean? = null
		waitingRead.whenComplete { _, _ -> failedUnderLock = Thread.holdsLock(inTest) }
		val otherVaultsRead = inTest.download(VaultPath(otherVault, "a")) { "other" }

		inTest.failWaitingReads(vault)

		assertFalse(waitingRead.isDone)

		locked = true
		inTest.failWaitingReads(vault)

		assertThrows<ExecutionException> { waitingRead.get(5, TimeUnit.SECONDS) }.also { assertInstanceOf(MissingCryptorException::class.java, it.cause) }
		assertEquals(false, failedUnderLock)
		assertEquals("other", otherVaultsRead.get(5, TimeUnit.SECONDS))

		locked = false
		val laterRead = inTest.download(path("a/x.txt")) { "later" }

		assertThrows<TimeoutException> { laterRead.get(200, TimeUnit.MILLISECONDS) }
		change.complete("done")

		assertEquals("later", laterRead.get(5, TimeUnit.SECONDS))
		assertFalse(waitingReadRan)
	}

	@Test
	fun `a read that found its vault unlocked right before a write found it locked fails with the waiting reads`() {
		var locked = false
		val checked = CountDownLatch(1)
		val proceed = CountDownLatch(1)
		inTest.isLocked = { vault ->
			val result = locked && vault == this.vault
			if (Thread.currentThread().name == "reader") {
				checked.countDown()
				proceed.await()
			}
			result
		}
		inTest.write(listOf(path("a"))) { CompletableFuture<String>() }
		val read = AtomicReference<CompletableFuture<String>>()
		val reader = thread(name = "reader") { read.set(inTest.read(path("a/x.txt")) { "read" }) }
		checked.await()

		locked = true
		val failing = thread { inTest.failWaitingReads(vault) }
		while (failing.state != Thread.State.BLOCKED && failing.state != Thread.State.TERMINATED) {
			Thread.onSpinWait()
		}
		proceed.countDown()
		reader.join()
		failing.join()

		assertThrows<ExecutionException> { read.get().get(5, TimeUnit.SECONDS) }.also { assertInstanceOf(MissingCryptorException::class.java, it.cause) }
	}

	@Test
	fun `a read failed because its vault is locked doesn't run, even if it was about to start`() {
		val inTest = CloudTaskScheduler(defaultExecutor = direct, downloadExecutor = direct)
		var locked = false
		inTest.isLocked = { locked && it == vault }
		val firstChange = CompletableFuture<String>()
		val secondChange = CompletableFuture<String>()
		inTest.write(listOf(path("a"))) { firstChange }
		inTest.write(listOf(path("b"))) { secondChange }
		val ran = mutableListOf<String>()
		val first = inTest.download(path("a/x.txt")) { ran.add("first") }
		val second = inTest.download(path("b/y.txt")) { ran.add("second") }
		// whichever read is failed first lets the other one start before that one is failed, too
		first.whenComplete { _, _ -> secondChange.complete("done") }
		second.whenComplete { _, _ -> firstChange.complete("done") }

		locked = true
		inTest.failWaitingReads(vault)

		assertTrue(first.isCompletedExceptionally)
		assertTrue(second.isCompletedExceptionally)
		assertEquals(emptyList<String>(), ran)
	}

	@Test
	fun `a read that already runs isn't failed when a write finds its vault locked, and keeps its path until it is done`() {
		var locked = false
		inTest.isLocked = { locked && it == vault }
		val started = CountDownLatch(1)
		val release = CountDownLatch(1)
		val read = inTest.download(path("a/x.txt")) {
			started.countDown()
			release.await()
			"read"
		}
		assertTrue(started.await(5, TimeUnit.SECONDS))

		locked = true
		inTest.failWaitingReads(vault)
		val laterWrite = inTest.upload(path("a/x.txt")) { "written" }

		assertThrows<TimeoutException> { laterWrite.get(200, TimeUnit.MILLISECONDS) }
		release.countDown()
		assertEquals("read", read.get(5, TimeUnit.SECONDS))
		assertEquals("written", laterWrite.get(5, TimeUnit.SECONDS))
	}

	@Test
	fun `a request keeps the order of requests made before it, not after it`() {
		val events = mutableListOf<String>()
		val firstStarted = CountDownLatch(1)
		val release = CountDownLatch(1)
		val first = write(listOf(path("a"))) {
			firstStarted.countDown()
			release.await()
			synchronized(events) { events.add("first") }
		}
		firstStarted.await()
		val second = inTest.read(path("a")) { synchronized(events) { events.add("second") } }
		val third = write(listOf(path("a"))) { synchronized(events) { events.add("third") } }

		release.countDown()
		listOf(first, second, third).forEach { it.get(5, TimeUnit.SECONDS) }

		assertEquals(listOf("first", "second", "third"), events)
	}

	@Test
	fun `a request at or below a renamed path waits for the requests that were pending at or below its old path`() {
		val release = CountDownLatch(1)
		val earlierStarted = CountDownLatch(1)
		write(listOf(path("a/x.txt"))) {
			earlierStarted.countDown()
			release.await()
		}
		assertTrue(earlierStarted.await(5, TimeUnit.SECONDS))
		inTest.renamed(path("a"), path("b"))
		val laterStarted = CountDownLatch(1)

		inTest.upload(path("b/y.txt")) { laterStarted.countDown() }

		assertFalse(laterStarted.await(200, TimeUnit.MILLISECONDS))
		release.countDown()
		assertTrue(laterStarted.await(5, TimeUnit.SECONDS))
	}

	@Test
	fun `a change of a renamed path waits for the requests that were pending below its old path`() {
		val release = CountDownLatch(1)
		val earlierStarted = CountDownLatch(1)
		write(listOf(path("a/x.txt"))) {
			earlierStarted.countDown()
			release.await()
		}
		assertTrue(earlierStarted.await(5, TimeUnit.SECONDS))
		inTest.renamed(path("a"), path("b"))
		val laterStarted = CountDownLatch(1)

		write(listOf(path("b"))) { laterStarted.countDown() }

		assertFalse(laterStarted.await(200, TimeUnit.MILLISECONDS))
		release.countDown()
		assertTrue(laterStarted.await(5, TimeUnit.SECONDS))
	}

	@Test
	fun `a request below a path renamed twice waits for the requests that were pending below its first path`() {
		val release = CountDownLatch(1)
		val earlierStarted = CountDownLatch(1)
		write(listOf(path("a/x.txt"))) {
			earlierStarted.countDown()
			release.await()
		}
		assertTrue(earlierStarted.await(5, TimeUnit.SECONDS))
		inTest.renamed(path("a"), path("b"))
		inTest.renamed(path("b"), path("c"))
		val laterStarted = CountDownLatch(1)

		inTest.upload(path("c/y.txt")) { laterStarted.countDown() }

		assertFalse(laterStarted.await(200, TimeUnit.MILLISECONDS))
		release.countDown()
		assertTrue(laterStarted.await(5, TimeUnit.SECONDS))
	}

	@Test
	fun `a request at a renamed path doesn't wait for requests made at its old path after the rename`() {
		inTest.renamed(path("a"), path("b"))
		val release = CountDownLatch(1)
		write(listOf(path("a/x.txt"))) { release.await() }
		val laterStarted = CountDownLatch(1)

		inTest.upload(path("b/y.txt")) { laterStarted.countDown() }

		assertTrue(laterStarted.await(5, TimeUnit.SECONDS))
		release.countDown()
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

	private fun <T> write(paths: List<VaultPath>, task: () -> T): CompletableFuture<T> {
		return inTest.write(paths) { CompletableFuture.completedFuture(task()) }
	}

	private fun path(path: String): VaultPath {
		return VaultPath(vault, path)
	}
}
