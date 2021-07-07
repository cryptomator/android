package org.cryptomator.data.util

import java.io.IOException
import java.io.InputStream

abstract class TransferredBytesAwareInputStream(private val inputStream: InputStream) : InputStream() {

	private var transferred: Long = 0

	@Throws(IOException::class)
	override fun read(): Int {
		val result = inputStream.read()
		if (result != EOF) {
			bytesTransferred(++transferred)
		}
		return result
	}

	@Throws(IOException::class)
	override fun read(b: ByteArray): Int {
		val result = inputStream.read(b)
		if (result != EOF) {
			transferred += result.toLong()
			bytesTransferred(transferred)
		}
		return result
	}

	@Throws(IOException::class)
	override fun read(b: ByteArray, off: Int, len: Int): Int {
		val result = inputStream.read(b, off, len)
		if (result != EOF) {
			transferred += result.toLong()
			bytesTransferred(transferred)
		}
		return result
	}

	@Throws(IOException::class)
	override fun close() {
		inputStream.close()
	}

	@Throws(IOException::class)
	override fun available(): Int {
		return inputStream.available()
	}

	@Throws(IOException::class)
	override fun skip(n: Long): Long {
		val result = inputStream.skip(n)
		transferred += result
		bytesTransferred(transferred)
		return result
	}

	abstract fun bytesTransferred(transferred: Long)

	companion object {

		private const val EOF = -1
	}
}
