package org.cryptomator.data.documents

import android.os.CancellationSignal
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Waits for a cloud request and rethrows what made it fail. Cloud requests never run on the binder thread, because it
 * takes over the StrictMode policy of the calling app. They would fail with a NetworkOnMainThreadException whenever an
 * app calls the provider from its main thread. If [signal] is canceled, waiting ends right away, but the request runs on
 * so that it keeps its place in the order of requests.
 */
internal fun <T> CompletableFuture<T>.await(signal: CancellationSignal? = null): T {
	val waiting = if (signal == null) this else CompletableFuture<T>().also { waiting ->
		whenComplete { result, e -> if (e != null) waiting.completeExceptionally(e) else waiting.complete(result) }
		signal.setOnCancelListener { waiting.cancel(false) }
	}
	try {
		return waiting.get()
	} catch (e: ExecutionException) {
		throw e.cause ?: e
	} catch (e: CancellationException) {
		signal?.throwIfCanceled()
		throw e
	} finally {
		signal?.setOnCancelListener(null)
	}
}

/**
 * Unwraps the [CompletionException] that dependent stages of a [CompletableFuture] receive.
 */
internal fun unwrap(e: Throwable): Throwable {
	return if (e is CompletionException) e.cause ?: e else e
}

/**
 * Modification dates are compared to the second because not every cloud reports milliseconds.
 */
internal fun isSameSecond(lastModified: Long?, otherLastModified: Long?): Boolean {
	return lastModified != null && otherLastModified != null && TimeUnit.MILLISECONDS.toSeconds(lastModified) == TimeUnit.MILLISECONDS.toSeconds(otherLastModified)
}

/**
 * The name the iOS app gives the copy of a file that changed both locally and in the cloud, e.g. `notes (1A2B3).txt`.
 */
internal fun conflictCopyName(name: String, suffix: String): String {
	val extensionStart = name.lastIndexOf('.')
	return if (extensionStart > 0) "${name.substring(0, extensionStart)} ($suffix)${name.substring(extensionStart)}" else "$name ($suffix)"
}
