package org.cryptomator.data.db.sqlmapping

internal class OneOffDelegate(private val beforeExtraCalls: () -> Unit) {

	private val lock = Any()
	private var called = false

	fun <R> call(delegateCallable: () -> R): R = synchronized(lock) {
		if (called) {
			beforeExtraCalls()
		}
		called = true
		return delegateCallable()
	}
}