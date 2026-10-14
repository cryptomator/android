package org.cryptomator.presentation.docprovider

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors

private const val MAX_PARALLEL_TRANSFERS = 2

/**
 * Orders the cloud requests of the documents provider like the iOS app does. A request reads or writes paths and reads
 * all of their ancestors. It starts once the earlier requests that write one of its paths are done; a write also waits
 * for the earlier requests that read its paths. So a change waits for the uploads and downloads below it, while
 * requests for unrelated paths run in parallel. Uploads and downloads run on executors of their own, at most
 * [MAX_PARALLEL_TRANSFERS] of each at a time.
 */
class CloudTaskScheduler(
	private val defaultExecutor: Executor = Executors.newCachedThreadPool(),
	private val uploadExecutor: Executor = Executors.newFixedThreadPool(MAX_PARALLEL_TRANSFERS),
	private val downloadExecutor: Executor = Executors.newFixedThreadPool(MAX_PARALLEL_TRANSFERS)
) {

	private val pendingReads = HashMap<VaultPath, MutableSet<CompletableFuture<*>>>()
	private val pendingWrites = HashMap<VaultPath, MutableSet<CompletableFuture<*>>>()

	fun <T> read(path: VaultPath, task: () -> T): CompletableFuture<T> {
		return schedule(defaultExecutor, listOf(path), emptyList(), task)
	}

	fun <T> write(paths: List<VaultPath>, task: () -> T): CompletableFuture<T> {
		return schedule(defaultExecutor, emptyList(), paths, task)
	}

	fun <T> upload(path: VaultPath, task: () -> T): CompletableFuture<T> {
		return schedule(uploadExecutor, emptyList(), listOf(path), task)
	}

	fun <T> download(path: VaultPath, task: () -> T): CompletableFuture<T> {
		return schedule(downloadExecutor, listOf(path), emptyList(), task)
	}

	@Synchronized
	private fun <T> schedule(executor: Executor, reads: List<VaultPath>, writes: List<VaultPath>, task: () -> T): CompletableFuture<T> {
		val readPaths = (reads + (reads + writes).flatMap { it.ancestors() }).toSet() - writes.toSet()
		val dependencies = (readPaths + writes).flatMap { pendingWrites[it].orEmpty() } + writes.flatMap { pendingReads[it].orEmpty() }
		val future = CompletableFuture.allOf(*dependencies.toTypedArray()) //
			.handle { _, _ -> } //
			.thenApplyAsync({ task() }, executor)
		readPaths.forEach { pendingReads.getOrPut(it) { HashSet() }.add(future) }
		writes.forEach { pendingWrites.getOrPut(it) { HashSet() }.add(future) }
		future.whenComplete { _, _ -> release(future, readPaths, writes) }
		return future
	}

	@Synchronized
	private fun release(future: CompletableFuture<*>, readPaths: Collection<VaultPath>, writes: Collection<VaultPath>) {
		readPaths.forEach { pendingReads.removeFrom(it, future) }
		writes.forEach { pendingWrites.removeFrom(it, future) }
	}

	private fun HashMap<VaultPath, MutableSet<CompletableFuture<*>>>.removeFrom(path: VaultPath, future: CompletableFuture<*>) {
		val futures = get(path) ?: return
		futures.remove(future)
		if (futures.isEmpty()) {
			remove(path)
		}
	}

	private fun VaultPath.ancestors(): Sequence<VaultPath> {
		return generateSequence(parent) { it.parent }
	}
}
