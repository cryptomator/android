package org.cryptomator.util

object Comparators {

	fun <T : Comparable<T>> naturalOrder(): Comparator<T> {
		return Comparator { o1: T, o2: T -> o1.compareTo(o2) }
	}
}
