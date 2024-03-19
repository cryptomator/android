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