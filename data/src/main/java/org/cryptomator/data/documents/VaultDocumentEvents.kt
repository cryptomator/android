package org.cryptomator.data.documents

import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.repository.VaultDocumentListener
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells its listeners about changes to the cached vault documents, whether the documents provider or the app made them.
 */
@Singleton
class VaultDocumentEvents @Inject constructor() {

	private val listeners = CopyOnWriteArraySet<VaultDocumentListener>()

	fun addListener(listener: VaultDocumentListener) {
		listeners.add(listener)
	}

	fun removeListener(listener: VaultDocumentListener) {
		listeners.remove(listener)
	}

	fun onChanged(folder: VaultPath) {
		listeners.forEach { it.onChanged(folder) }
	}
}
