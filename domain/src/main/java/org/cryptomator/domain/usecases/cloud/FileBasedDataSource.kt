package org.cryptomator.domain.usecases.cloud

import android.content.Context
import org.cryptomator.util.Optional
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Date

class FileBasedDataSource private constructor(private val file: File) : DataSource {

	override fun size(context: Context): Long {
		return file.length()
	}
	override fun modifiedDate(context: Context): Date? {
		return Date(file.lastModified())
	}
	@Throws(IOException::class)
	override fun open(context: Context): InputStream {
		return FileInputStream(file)
	}

	override fun decorate(delegate: DataSource): DataSource {
		return delegate
	}

	@Throws(IOException::class)
	override fun close() {
		// Do nothing
	}

	override fun modifiedDate(context: Context): Optional<Date> {
		return Optional.of(Date(file.lastModified()))
	}

	companion object {

		@JvmStatic
		fun from(file: File): FileBasedDataSource {
			return FileBasedDataSource(file)
		}
	}
}
