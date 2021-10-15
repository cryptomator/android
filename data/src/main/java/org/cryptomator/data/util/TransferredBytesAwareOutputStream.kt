package org.cryptomator.data.util

import java.io.IOException
import java.io.OutputStream

abstract class TransferredBytesAwareOutputStream(private val outputStream: OutputStream) : OutputStream() {

	private var transferred: Long = 0

	@Throws(IOException::class)
	override fun write(b: ByteArray) {
		outputStream.write(b)
		transferred += b.size.toLong()
		bytesTransferred(transferred)
	}

	@Throws(IOException::class)
	override fun write(b: ByteArray, off: Int, len: Int) {
		outputStream.write(b, off, len)
		transferred += len.toLong()
		bytesTransferred(transferred)
	}

	@Throws(IOException::class)
	override fun write(i: Int) {
		outputStream.write(i)
		bytesTransferred(++transferred)
	}

	@Throws(IOException::class)
	override fun close() {
		outputStream.close()
	}

	@Throws(IOException::class)
	override fun flush() {
		outputStream.flush()
	}

	abstract fun bytesTransferred(transferred: Long)
}
