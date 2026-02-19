package org.cryptomator.domain.usecases.cloud

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class StreamHelperTest {

	@Test
	@DisplayName("copy transfers all bytes from input to output")
	fun copyTransfersAllBytes() {
		val data = byteArrayOf(1, 2, 3, 4, 5, 42, 127, -1)
		val input = ByteArrayInputStream(data)
		val output = ByteArrayOutputStream()

		StreamHelper.copy(input, output)

		assertThat(output.toByteArray(), `is`(data))
	}

	@Test
	@DisplayName("copy handles empty stream")
	fun copyHandlesEmptyStream() {
		val input = ByteArrayInputStream(ByteArray(0))
		val output = ByteArrayOutputStream()

		StreamHelper.copy(input, output)

		assertThat(output.toByteArray(), `is`(ByteArray(0)))
	}

	@Test
	@DisplayName("copy closes both streams on success")
	fun copyClosesStreamsOnSuccess() {
		val input: InputStream = mock {
			on { read(any<ByteArray>()) } doReturn -1
		}
		val output: OutputStream = mock()

		StreamHelper.copy(input, output)

		verify(input).close()
		verify(output).close()
	}

	@Test
	@DisplayName("copy closes both streams when read throws IOException")
	fun copyClosesStreamsOnReadError() {
		val input: InputStream = mock {
			on { read(any<ByteArray>()) } doThrow IOException("read failed")
		}
		val output: OutputStream = mock()

		try {
			StreamHelper.copy(input, output)
		} catch (_: IOException) {
		}

		verify(input).close()
		verify(output).close()
	}

	@Test
	@DisplayName("copy closes both streams when write throws IOException")
	fun copyClosesStreamsOnWriteError() {
		val data = byteArrayOf(1, 2, 3)
		val input = ByteArrayInputStream(data)
		val output: OutputStream = mock {
			on { write(any<ByteArray>(), any(), any()) } doThrow IOException("write failed")
		}

		try {
			StreamHelper.copy(input, output)
		} catch (_: IOException) {
		}

		verify(output).close()
	}

	@Test
	@DisplayName("closeQuietly does not throw on null")
	fun closeQuietlyHandlesNull() {
		assertDoesNotThrow {
			StreamHelper.closeQuietly(null)
		}
	}

	@Test
	@DisplayName("closeQuietly swallows IOException from close")
	fun closeQuietlySwallowsException() {
		val closeable: Closeable = mock {
			on { close() } doThrow IOException("close failed")
		}

		assertDoesNotThrow {
			StreamHelper.closeQuietly(closeable)
		}
	}
}
