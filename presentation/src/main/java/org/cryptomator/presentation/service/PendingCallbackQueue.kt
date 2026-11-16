package org.cryptomator.presentation.service

class PendingCallbackQueue<T> {

	private val pending = mutableListOf<(T) -> Unit>()

	@Volatile
	var isReady = false
		private set

	@Synchronized
	fun enqueueUnlessReady(callback: (T) -> Unit): Boolean {
		if (isReady) {
			return false
		}
		pending.add(callback)
		return true
	}

	@Synchronized
	fun markReadyAndDrain(): List<(T) -> Unit>? {
		isReady = true
		if (pending.isEmpty()) {
			return null
		}
		val snapshot = ArrayList(pending)
		pending.clear()
		return snapshot
	}

	@Synchronized
	fun markNotReady() {
		isReady = false
	}
}
