package org.cryptomator.domain.usecases.cloud

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

object EmptyDataSource : DataSource {

	private val EMPTY_ARRAY = ByteArray(0)

	override fun size(context: Context): Long = 0

	@Throws(IOException::class)
	override fun open(context: Context): InputStream {
		return ByteArrayInputStream(EMPTY_ARRAY)
	}

	override fun decorate(delegate: DataSource): DataSource {
		return delegate //TODO Verify
	}

	@Throws(IOException::class)
	override fun close() {
		// do nothing because ByteArrayInputStream need no close
		// see: ByteArrayDataSource
	}
}
