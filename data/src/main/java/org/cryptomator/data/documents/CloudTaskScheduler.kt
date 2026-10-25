package org.cryptomator.data.documents

import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.MissingCryptorException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val MAX_PARALLEL_TRANSFERS = 2

/**
 * Orders the cloud requests of the documents provider like the iOS app does. A request reads or writes paths and reads
 * all of their ancestors. It starts once the earlier requests that write one of its paths are done; a write also waits
 * for the earlier requests that read its paths. So a change waits for the uploads and downloads below it, while
 * requests for unrelated paths run in parallel. A read of a locked vault fails right away instead, see [isLocked].
 * Uploads and downloads run on executors of their own, at most [MAX_PARALLEL_TRANSFERS] of each at a time.
 */
class CloudTaskScheduler(
	private val defaultExecutor: Executor = Executors.newCachedThreadPool(),
	private val uploadExecutor: Executor = Executors.newFixedThreadPool(MAX_PARALLEL_TRANSFERS),
	private val downloadExecutor: Executor = Executors.newFixedThreadPool(MAX_PARALLEL_TRANSFERS)
) {

	/**
	 * The executor of the reads and writes, for work that needs no order, e.g. a callback that mustn't run on the thread
	 * that registers it.
	 */
	val unordered: Executor
		get() = defaultExecutor

	/**
	 * Whether a vault is locked, so that its reads fail rather than wait for a write that waits for the vault to be
	 * unlocked, see [write] and [failWaitingReads].
	 */
	@Volatile
	var isLocked: (Vault) -> Boolean = { false }

	private val pendingReads = HashMap<VaultPath, MutableSet<CompletableFuture<*>>>()
	private val pendingWrites = HashMap<VaultPath, MutableSet<CompletableFuture<*>>>()

	/**
	 * The reads that haven't started yet, by vault id.
	 */
	private val waitingReads = HashMap<Long, MutableSet<CompletableFuture<*>>>()

	fun <T> read(path: VaultPath, task: () -> T): CompletableFuture<T> {
		return schedule(defaultExecutor, listOf(path), emptyList()) { CompletableFuture.completedFuture(task()) }
	}

	/**
	 * The write is done once the future that [task] returns is, so that e.g. a change waiting for its vault to be unlocked
	 * keeps the later requests of [paths] waiting.
	 */
	fun <T> write(paths: List<VaultPath>, task: () -> CompletableFuture<T>): CompletableFuture<T> {
		return schedule(defaultExecutor, emptyList(), paths, task)
	}

	fun <T> upload(path: VaultPath, task: () -> T): CompletableFuture<T> {
		return schedule(uploadExecutor, emptyList(), listOf(path)) { CompletableFuture.completedFuture(task()) }
	}

	fun <T> download(path: VaultPath, task: () -> T): CompletableFuture<T> {
		return schedule(downloadExecutor, listOf(path), emptyList()) { CompletableFuture.completedFuture(task()) }
	}

	/**
	 * Fails the reads of [vault] that haven't started yet, so that none of them waits behind a write that found the vault
	 * locked. Nothing happens if the vault isn't locked.
	 */
	fun failWaitingReads(vault: Vault) {
		val reads = synchronized(this) {
			if (!isLocked(vault)) {
				return
			}
			waitingReads.remove(vault.id).orEmpty()
		}
		reads.forEach { it.completeExceptionally(MissingCryptorException()) }
	}

	private fun <T> schedule(executor: Executor, reads: List<VaultPath>, writes: List<VaultPath>, task: () -> CompletableFuture<T>): CompletableFuture<T> {
		val future = CompletableFuture<T>()
		val vault = (reads + writes).first().vault
		val isRead = writes.isEmpty()
		val readPaths = (reads + (reads + writes).flatMap { it.ancestors() }).toSet() - writes.toSet()
		val dependencies = synchronized(this) {
			// under the lock of failWaitingReads, so that a read can't start waiting right after the reads were failed
			if (isRead && isLocked(vault)) {
				null
			} else {
				val dependencies = (readPaths + writes).flatMap { pendingWrites[it].orEmpty() } + writes.flatMap { pendingReads[it].orEmpty() }
				readPaths.forEach { pendingReads.getOrPut(it) { HashSet() }.add(future) }
				writes.forEach { pendingWrites.getOrPut(it) { HashSet() }.add(future) }
				if (isRead) {
					waitingReads.getOrPut(vault.id) { HashSet() }.add(future)
				}
				dependencies
			}
		}
		if (dependencies == null) {
			future.completeExceptionally(MissingCryptorException())
			return future
		}
		future.whenComplete { _, _ -> release(future, vault, readPaths, writes) }
		CompletableFuture.allOf(*dependencies.toTypedArray()) //
			.handle { _, _ -> } //
			.thenAcceptAsync({ start(future, vault, isRead, task) }, executor)
		return future
	}

	private fun <T> start(future: CompletableFuture<T>, vault: Vault, isRead: Boolean, task: () -> CompletableFuture<T>) {
		// a read that is no longer waiting was failed by failWaitingReads
		val failedWhileWaiting = synchronized(this) {
			isRead && !waitingReads.removeFrom(vault.id, future)
		}
		if (failedWhileWaiting) {
			return
		}
		try {
			task().whenComplete { value, e -> if (e == null) future.complete(value) else future.completeExceptionally(unwrap(e)) }
		} catch (e: Throwable) {
			future.completeExceptionally(e)
		}
	}

	@Synchronized
	private fun release(future: CompletableFuture<*>, vault: Vault, readPaths: Collection<VaultPath>, writes: Collection<VaultPath>) {
		readPaths.forEach { pendingReads.removeFrom(it, future) }
		writes.forEach { pendingWrites.removeFrom(it, future) }
		waitingReads.removeFrom(vault.id, future)
	}

	private fun <K> HashMap<K, MutableSet<CompletableFuture<*>>>.removeFrom(key: K, future: CompletableFuture<*>): Boolean {
		val futures = get(key) ?: return false
		val removed = futures.remove(future)
		if (futures.isEmpty()) {
			remove(key)
		}
		return removed
	}

	private fun VaultPath.ancestors(): Sequence<VaultPath> {
		return generateSequence(parent) { it.parent }
	}
}
