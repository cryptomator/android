package org.cryptomator.data.util

import org.cryptomator.domain.exception.FatalBackendException
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object CopyStream {

	private const val DEFAULT_COPY_BUFFER_SIZE = 16 shl 10 // 16 KiB

	fun copyStreamToStream(inputStream: InputStream, out: OutputStream) {
		copyStreamToStream(inputStream, out, ByteArray(DEFAULT_COPY_BUFFER_SIZE))
	}

	private fun copyStreamToStream(inputStream: InputStream, out: OutputStream, copyBuffer: ByteArray) {
		while (true) {
			val count: Int = try {
				inputStream.read(copyBuffer)
			} catch (ex: IOException) {
				throw FatalBackendException(ex)
			}
			if (count == -1) {
				break
			}
			try {
				out.write(copyBuffer, 0, count)
			} catch (ex: IOException) {
				throw FatalBackendException(ex)
			}
		}
	}

	fun closeQuietly(closeable: Closeable?) {
		if (closeable != null) {
			try {
				closeable.close()
			} catch (rethrown: RuntimeException) {
				throw rethrown
			} catch (e: IOException) {
				// ignore
			}
		}
	}

	fun toByteArray(inputStream: InputStream): ByteArray {
		val buffer = ByteArrayOutputStream()
		var read: Int
		val data = ByteArray(1024)
		try {
			while (inputStream.read(data, 0, data.size).also { read = it } != -1) {
				buffer.write(data, 0, read)
			}
			buffer.flush()
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
		return buffer.toByteArray()
	}
}
