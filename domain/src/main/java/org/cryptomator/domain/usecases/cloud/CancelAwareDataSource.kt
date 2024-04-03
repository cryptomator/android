package org.cryptomator.domain.usecases.cloud

import android.content.Context
import org.cryptomator.domain.exception.CancellationException
import java.io.IOException
import java.io.InputStream
import java.util.Date

class CancelAwareDataSource private constructor(private val delegate: DataSource, private val cancelled: Flag) : DataSource {

	override fun modifiedDate(context: Context): Date? {
		if (cancelled.get()) {
			throw CancellationException()
		}
		return delegate.modifiedDate(context)
	}

	override fun size(context: Context): Long? {
		if (cancelled.get()) {
			throw CancellationException()
		}
		return delegate.size(context)
	}

	@Throws(IOException::class)
	override fun open(context: Context): InputStream {
		if (cancelled.get()) {
			throw CancellationException()
		}
		return CancelAwareInputStream.wrap(delegate.open(context), cancelled)
	}

	override fun decorate(delegate: DataSource): CancelAwareDataSource {
		return CancelAwareDataSource(delegate, cancelled)
	}

	@Throws(IOException::class)
	override fun close() {
		delegate.close()
	}

	companion object {

		@JvmStatic
		fun wrap(delegate: DataSource, cancelled: Flag): CancelAwareDataSource {
			return CancelAwareDataSource(delegate, cancelled)
		}
	}
}
