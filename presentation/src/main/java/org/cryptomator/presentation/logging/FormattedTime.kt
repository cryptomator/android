package org.cryptomator.presentation.logging

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class FormattedTime private constructor(private val timestamp: Long) {

	@Volatile
	private var formatted: String? = null

	@Synchronized
	override fun toString(): String {
		return formatted ?: SimpleDateFormat(FORMAT, Locale.getDefault()).format(Date(timestamp)).also { formatted = it }
	}

	companion object {

		private const val FORMAT = "yyyyMMddHHmmss.SSS"

		@Volatile
		private var now = FormattedTime(0)
		fun now(): FormattedTime {
			var localNow = now
			if (localNow.timestamp < System.currentTimeMillis()) {
				localNow = FormattedTime(System.currentTimeMillis())
				now = localNow
			}
			return localNow
		}
	}
}
