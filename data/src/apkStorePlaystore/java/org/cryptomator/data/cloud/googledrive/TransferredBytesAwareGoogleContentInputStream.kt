package org.cryptomator.data.cloud.googledrive

import com.google.api.client.http.AbstractInputStreamContent
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

abstract class TransferredBytesAwareGoogleContentInputStream(type: String?, data: InputStream, size: Long) : AbstractInputStreamContent(type), Closeable {

	private val data: InputStream
	private val size: Long

	@Throws(IOException::class)
	override fun getInputStream(): InputStream {
		return data
	}

	@Throws(IOException::class)
	override fun getLength(): Long {
		return size
	}

	override fun retrySupported(): Boolean {
		return false
	}

	@Throws(IOException::class)
	override fun close() {
		data.close()
	}

	abstract fun bytesTransferred(transferred: Long)

	/**
	 * @param size the size of the data to upload or less than zero if not known
	 */
	init {
		this.data = object : TransferredBytesAwareInputStream(data) {
			override fun bytesTransferred(transferred: Long) {
				this@TransferredBytesAwareGoogleContentInputStream.bytesTransferred(transferred)
			}
		}
		this.size = size
	}
}
