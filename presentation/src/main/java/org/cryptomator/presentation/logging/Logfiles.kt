package org.cryptomator.presentation.logging

import android.content.Context
import java.io.File
import java.util.ArrayList

object Logfiles {

	const val NUMBER_OF_LOGFILES = 2

	/**
	 * Maximum size of all logfiles
	 */
	private const val MAX_LOGS_SIZE = (1 shl 20 // 1 MiB
			.toLong().toInt()).toLong()

	/**
	 * When this size is reached a logfile is rotated
	 */
	const val ROTATION_FILE_SIZE = MAX_LOGS_SIZE / NUMBER_OF_LOGFILES

	@JvmStatic
	fun logfiles(context: Context): List<File> {
		return (0 until NUMBER_OF_LOGFILES).mapTo(ArrayList(NUMBER_OF_LOGFILES)) { logfile(context, it) }
	}

	fun logsDir(context: Context): File {
		return File(context.cacheDir, "logs")
	}

	fun existingLogfiles(context: Context): List<File> {
		return logfiles(context).filterTo(ArrayList(NUMBER_OF_LOGFILES)) { it.exists() }
	}

	private fun logfile(context: Context, index: Int): File {
		return File(logsDir(context), logfileName(index))
	}

	private fun logfileName(index: Int): String {
		return "$index.txt"
	}
}
