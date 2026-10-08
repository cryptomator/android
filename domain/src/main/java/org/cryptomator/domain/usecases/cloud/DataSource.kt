package org.cryptomator.domain.usecases.cloud

import android.content.Context
import org.cryptomator.util.Optional
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.Serializable
import java.util.Date

interface DataSource : Serializable, Closeable {

	fun size(context: Context): Long?

	/**
	 * Opens a new stream on every invocation so that the data can be read more than once, which is required whenever a
	 * request carrying it has to be repeated. see https://github.com/cryptomator/android/issues/646
	 */
	@Throws(IOException::class)
	fun open(context: Context): InputStream?

	fun decorate(delegate: DataSource): DataSource

	fun modifiedDate(context: Context): Optional<Date>

}
