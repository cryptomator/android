package org.cryptomator.data.cloud.webdav.network

import android.content.Context
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.util.Optional
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Date
import okio.Buffer

class DataSourceBasedRequestBodyTest {

	private val context = mock<Context>()

	private lateinit var data: CountingDataSource

	@BeforeEach
	fun setup() {
		data = CountingDataSource(ByteArrayDataSource.from(CONTENT))
	}

	@Test
	@DisplayName("contentLength() returns the size the request body was created with")
	fun testContentLengthReturnsTheSizeTheRequestBodyWasCreatedWith() {
		val inTest = DataSourceBasedRequestBody.from(context, data, CONTENT.size.toLong()) { it }

		MatcherAssert.assertThat(inTest.contentLength(), CoreMatchers.`is`(CONTENT.size.toLong()))
	}

	@Test
	@DisplayName("isOneShot() is false because the request body can be written more than once")
	fun testIsOneShotIsFalse() {
		val inTest = DataSourceBasedRequestBody.from(context, data, CONTENT.size.toLong()) { it }

		MatcherAssert.assertThat(inTest.isOneShot(), CoreMatchers.`is`(false))
	}

	/**
	 * OkHttp repeats a request when it used a pooled connection the server closed in the meantime, e.g. because of its
	 * keep-alive timeout. Writing the request body a second time has to write the complete content again instead of
	 * failing on the stream consumed by the first attempt. see https://github.com/cryptomator/android/issues/646
	 */
	@Test
	@DisplayName("writeTo(…) writes the complete content on every attempt")
	fun testWriteToWritesTheCompleteContentOnEveryAttempt() {
		val inTest = DataSourceBasedRequestBody.from(context, data, CONTENT.size.toLong()) { it }

		val firstAttempt = Buffer()
		inTest.writeTo(firstAttempt)
		val secondAttempt = Buffer()
		inTest.writeTo(secondAttempt)

		MatcherAssert.assertThat(firstAttempt.readByteArray(), CoreMatchers.`is`(CONTENT))
		MatcherAssert.assertThat(secondAttempt.readByteArray(), CoreMatchers.`is`(CONTENT))
		MatcherAssert.assertThat(data.opened, CoreMatchers.`is`(2))
	}

	@Test
	@DisplayName("writeTo(…) reports the transferred bytes of every attempt")
	fun testWriteToReportsTheTransferredBytesOfEveryAttempt() {
		val reported = ArrayList<Long>()
		val inTest = DataSourceBasedRequestBody.from(context, data, CONTENT.size.toLong()) { inputStream ->
			object : TransferredBytesAwareInputStream(inputStream) {
				override fun bytesTransferred(transferred: Long) {
					reported.add(transferred)
				}
			}
		}

		inTest.writeTo(Buffer())
		reported.clear()
		inTest.writeTo(Buffer())

		MatcherAssert.assertThat(reported.lastOrNull(), CoreMatchers.`is`(CONTENT.size.toLong()))
	}

	private class CountingDataSource(private val delegate: DataSource) : DataSource {

		var opened = 0
			private set

		override fun size(context: Context): Long? {
			return delegate.size(context)
		}

		override fun open(context: Context): InputStream? {
			opened++
			return delegate.open(context)
		}

		override fun decorate(delegate: DataSource): DataSource {
			return delegate
		}

		override fun close() {
			delegate.close()
		}

		override fun modifiedDate(context: Context): Optional<Date> {
			return delegate.modifiedDate(context)
		}
	}

	companion object {

		private val CONTENT = "Wer die Wahl hat, hat die Qual".toByteArray(StandardCharsets.UTF_8)

	}
}
