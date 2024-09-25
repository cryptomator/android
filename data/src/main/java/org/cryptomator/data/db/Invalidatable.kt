package org.cryptomator.data.db

import java.util.concurrent.Callable

/**
 * Instances of this class are thread-safe [Callables][Callable] that cache their results after the first [call,][call] similar to [Lazy.][Lazy]
 * However, they allow the stored result to be invalidated with [invalidate,][invalidate] after which the result is recalculated the next time [call] is invoked.
 */
class Invalidatable<T>(private val delegate: Callable<T>) : Callable<T> {

	@Volatile
	private var instance: Any? = UNINITIALIZED

	override fun call(): T {
		synchronized(this) {
			if (instance === UNINITIALIZED) {
				instance = delegate.call()
			}
			@Suppress("UNCHECKED_CAST") //
			return instance as T
		}
	}

	fun invalidate() {
		synchronized(this) {
			instance = UNINITIALIZED
		}
	}

	companion object {

		private val UNINITIALIZED = object {
			override fun toString(): String = "UNINITIALIZED"
		}
	}
}