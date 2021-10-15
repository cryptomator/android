package org.cryptomator.domain.usecases.cloud

import android.content.Context
import org.cryptomator.domain.exception.FatalBackendException
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.mock
import org.mockito.stubbing.Answer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

internal class DataSourceCapturingAnswer<T>(private val result: T, private val argIndex: Int) : Answer<T> {

	private lateinit var out: ByteArrayOutputStream

	private val context : Context = mock()

	@Throws(Throwable::class)
	override fun answer(invocation: InvocationOnMock): T {
		val inputStream = (invocation.arguments[argIndex] as DataSource).open(context)!!
		out = ByteArrayOutputStream()
		copy(inputStream, out)
		return result
	}

	private fun copy(inputStream: InputStream, out: ByteArrayOutputStream) {
		val buffer = ByteArray(4096)
		var read: Int
		try {
			while (inputStream.read(buffer).also { read = it } != -1) {
				out.write(buffer, 0, read)
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	fun toByteArray(): ByteArray {
		return out.toByteArray()
	}
}
