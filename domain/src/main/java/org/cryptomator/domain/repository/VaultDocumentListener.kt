package org.cryptomator.domain.repository

import org.cryptomator.domain.VaultPath

fun interface VaultDocumentListener {

	/**
	 * Called whenever the children of [folder] changed, on the thread that changed them, often while the change still
	 * holds the cache: a listener must not wait for the cache.
	 */
	fun onChanged(folder: VaultPath)
}
