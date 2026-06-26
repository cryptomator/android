package org.cryptomator.presentation.service

class PendingCallbackQueue<T> {

	private val pending = mutableListOf<(T) -> Unit>()

	@Synchronized
	fun enqueue(callback: (T) -> Unit) {
		pending.add(callback)
	}

	@Synchronized
	fun drainSnapshot(): List<(T) -> Unit>? {
		if (pending.isEmpty()) {
			return null
		}
		val snapshot = ArrayList(pending)
		pending.clear()
		return snapshot
	}
}
