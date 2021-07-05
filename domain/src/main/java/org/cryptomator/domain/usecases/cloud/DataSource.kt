package org.cryptomator.domain.usecases.cloud

import android.content.Context
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.Serializable

interface DataSource : Serializable, Closeable {

	fun size(context: Context): Long?

	@Throws(IOException::class)
	fun open(context: Context): InputStream?

	fun decorate(delegate: DataSource): DataSource

}
