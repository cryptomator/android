@file:JvmName("Utils")

package org.cryptomator.data.util

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