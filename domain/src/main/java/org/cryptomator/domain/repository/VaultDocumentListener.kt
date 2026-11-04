package org.cryptomator.domain.repository

import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState

/**
 * Told about the cached vault documents on the thread that changed them, often while the change still holds the cache:
 * a listener must not wait for the cache.
 */
fun interface VaultDocumentListener {

	/**
	 * Called whenever the children of [folder] changed.
	 */
	fun onChanged(folder: VaultPath)

	/**
	 * Called when a change failed and was undone in the cache, or when an upload failed. [path] is where the change put
	 * the node, e.g. a move's target. [userInitiated] tells whether the user asked for the change or retry, rather than an
	 * automatic retry or another app.
	 */
	fun onChangeFailed(path: VaultPath, error: Throwable, userInitiated: Boolean) {}

	fun onUploadProgress(path: VaultPath, progress: Progress<UploadState>) {}
}
