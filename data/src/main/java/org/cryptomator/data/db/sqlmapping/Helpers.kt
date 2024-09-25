package org.cryptomator.data.db.sqlmapping

import android.content.ContentValues
import android.os.Build

internal fun ContentValues.compatIsEmpty(): Boolean {
	return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
		isEmpty
	} else {
		size() == 0
	}
}

internal fun <E> MutableList<E?>.setLeniently(index: Int, element: E?): E? {
	val limit = index - size
	for (i in 0..limit) { //The size of the range is 0 if limit < 0, 1 if limit == 0 and limit + 1 else.
		add(null)
	}
	return set(index, element)
}

internal inline fun <reified T> Sequence<T>.toArray(size: Int): Array<T> {
	val iterator = iterator()
	return Array(size) { iterator.next() }
}