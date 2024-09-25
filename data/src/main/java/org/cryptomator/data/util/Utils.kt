@file:JvmName("Utils")

package org.cryptomator.data.util

import java.io.Closeable

fun String?.blankToNull(): String? {
	return if (isNullOrBlank()) null else this
}

fun <T> Array<T>?.emptyToNull(): Array<T>? {
	return if (isNullOrEmpty()) null else this
}

fun String?.requireNullOrNotBlank(): String? {
	if (this != null && this.isBlank()) {
		throw IllegalArgumentException("String is blank")
	}
	return this
}

/**
 * Executes the given [tryBlock] function on this object and then always executes the [finallyBlock]
 * whether an exception is thrown or not, similar to a regular `finally` block.
 *
 * When using a regular `finally` block, an exception thrown by it will cause any exceptions
 * thrown by the `try` block to be discarded.
 * In contrast, [use][kotlin.io.use] and this method ensure that exceptions thrown by the [finallyBlock] do not suppress
 * exceptions thrown by the [tryBlock.][tryBlock] If both the [tryBlock] and the [finallyBlock] throw exceptions,
 * the exception from the [finallyBlock] is added to the list of suppressed exceptions of the exception
 * thrown by the [tryBlock.][tryBlock]
 *
 * This method effectively turns any object into a [Closeable,][Closeable] where the [finallyBlock] is used as the implementation of the [Closeable.close] method
 * and the [tryBlock] is executed on it with [use.][kotlin.io.use]
 *
 * @see kotlin.io.use
 */
inline fun <T, R> T.useFinally(tryBlock: (T) -> R, crossinline finallyBlock: (T) -> Unit): R {
	return Closeable {
		finallyBlock(this)
	}.use {
		tryBlock(this)
	}
}