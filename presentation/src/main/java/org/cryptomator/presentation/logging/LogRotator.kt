package org.cryptomator.presentation.logging

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter

internal class LogRotator(context: Context) {

	private val context: Context
	private val logfiles: List<File>

	@Volatile
	private var logfile: Logfile

	private fun createLogDirIfMissing() {
		val logsDir = Logfiles.logsDir(context)
		check(!(!logsDir.exists() && !logsDir.mkdirs())) { "Creating logs dir failed" }
	}

	private fun indexOfNewestLogfile(): Int {
		var index = 0
		var newestLastModified = 0L
		logfiles.indices.forEach { i ->
			val lastModified = logfiles[i].lastModified()
			if (lastModified > newestLastModified) {
				index = i
				newestLastModified = lastModified
			}
		}
		return index
	}

	fun log(message: String?) {
		logfile().log(message)
	}

	private fun logfile(): Logfile {
		// correct and fast double checked locking approach
		var localLogfile = logfile
		if (localLogfile.mustBeRotated()) {
			synchronized(this) {
				localLogfile = logfile
				if (localLogfile.mustBeRotated()) {
					logfile = localLogfile.next()
					localLogfile = logfile
				}
			}
		}
		return localLogfile
	}

	private inner class Logfile(index: Int, deleteIfPresent: Boolean = false) {

		private val index: Int = index % logfiles.size

		private val writer: PrintWriter

		private lateinit var measuringOutputStream: SizeMeasuringOutputStream

		fun mustBeRotated(): Boolean {
			return measuringOutputStream.size() > Logfiles.ROTATION_FILE_SIZE
		}

		private fun open(logfile: File, deleteIfPresent: Boolean): PrintWriter {
			return try {
				if (deleteIfPresent && logfile.exists()) {
					check(logfile.delete()) { "Failed to delete log file" }
				}
				measuringOutputStream = SizeMeasuringOutputStream(FileOutputStream(logfile, true))
				PrintWriter(measuringOutputStream, true)
			} catch (e: IOException) {
				throw IllegalStateException("Opening ", e)
			}
		}

		operator fun next(): Logfile {
			writer.close()
			return Logfile(index + 1, true)
		}

		fun log(message: String?) {
			writer.println(message)
		}

		init {
			writer = open(logfiles[this.index], deleteIfPresent)
		}
	}

	init {
		check(Logfiles.NUMBER_OF_LOGFILES >= 2) { "LogRotator needs at least two logfiles" }
		this.context = context
		createLogDirIfMissing()
		logfiles = Logfiles.logfiles(context)
		logfile = Logfile(indexOfNewestLogfile())
	}
}
