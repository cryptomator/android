package org.cryptomator.presentation.logging

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicLong

internal class SizeMeasuringOutputStream(private val delegate: OutputStream) : OutputStream() {

	private val size = AtomicLong(0)

	fun size(): Long {
		return size.get()
	}

	@Throws(IOException::class)
	override fun write(b: Int) {
		delegate.write(b)
		size.incrementAndGet()
	}

	@Throws(IOException::class)
	override fun write(b: ByteArray) {
		delegate.write(b)
		size.addAndGet(b.size.toLong())
	}

	@Throws(IOException::class)
	override fun write(b: ByteArray, off: Int, len: Int) {
		delegate.write(b, off, len)
		size.addAndGet(len.toLong())
	}

	@Throws(IOException::class)
	override fun flush() {
		delegate.flush()
	}

	@Throws(IOException::class)
	override fun close() {
		delegate.close()
	}
}
