package org.cryptomator.domain.usecases.cloud

import android.content.Context
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays

class UploadFileTest {

	private val context :Context = mock()
	private var cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private val parent :CloudFolder = mock()
	private val targetFile :CloudFile = mock()
	private val resultFile :CloudFile = mock()

	private val progressAware: ProgressAware<UploadState> = mock()

	private val fileName = "fileName"

	private fun <T> any(type: Class<T>): T = Mockito.any(type)

	@ParameterizedTest
	@ValueSource(booleans = [true, false])
	@Throws(BackendException::class)
	fun testInvocationWithFileSizeDelegatesToCloudContentRepository(replacing: Boolean) {
		val fileSize: Long = 1337
		val dataSource = dataSourceWithBytes(0, fileSize, fileSize)
		val inTest = testCandidate(dataSource, replacing)

		 whenever(cloudContentRepository.file(parent, fileName, fileSize)).thenReturn(targetFile)
		 whenever(
			cloudContentRepository.write(
				same(targetFile),
				any(DataSource::class.java),
				same(progressAware),
				eq(replacing),
				eq(fileSize)
			)
		).thenReturn(resultFile)

		val result = inTest.execute(progressAware)

		MatcherAssert.assertThat(result.size, CoreMatchers.`is`(1))
		MatcherAssert.assertThat(result[0], CoreMatchers.`is`(resultFile))
	}

	@ParameterizedTest
	@ValueSource(booleans = [true, false])
	@Throws(BackendException::class, IOException::class)
	fun testInvocationWithoutFileSizeDelegatesToCloudContentRepository(replacing: Boolean) {
		val fileSize: Long = 8893
		dataSourceWithBytes(85, fileSize, null).use { dataSource ->
			val inTest = testCandidate(dataSource, replacing)
			 whenever(cloudContentRepository.file(parent, fileName, fileSize)).thenReturn(targetFile)
			val capturedStreamData = DataSourceCapturingAnswer<Any?>(resultFile, 1)
			 whenever(
				cloudContentRepository.write(
					same(targetFile),
					any(DataSource::class.java),
					same(progressAware),
					eq(replacing),
					eq(fileSize)
				)
			).thenAnswer(capturedStreamData)

			val result = inTest.execute(progressAware)

			MatcherAssert.assertThat(result.size, CoreMatchers.`is`(1))
			MatcherAssert.assertThat(result[0], CoreMatchers.`is`(resultFile))
			MatcherAssert.assertThat(capturedStreamData.toByteArray(), CoreMatchers.`is`(bytes(85, fileSize)))
		}
	}

	private fun dataSourceWithBytes(value: Int, amount: Long, size: Long?): DataSource {
		check(amount <= Int.MAX_VALUE) { "Can not use values > Integer.MAX_VALUE" }
		val bytes = bytes(value, amount)
		return object : DataSource {
			override fun size(context: Context): Long? {
				return size
			}

			@Throws(IOException::class)
			override fun open(context: Context): InputStream {
				return ByteArrayInputStream(bytes)
			}

			override fun decorate(delegate: DataSource): DataSource {
				return delegate
			}

			@Throws(IOException::class)
			override fun close() {
				// do nothing
			}
		}
	}

	private fun bytes(value: Int, amount: Long): ByteArray {
		check(amount <= Int.MAX_VALUE) { "Can not use values > Integer.MAX_VALUE" }
		val data = ByteArray(amount.toInt())
		Arrays.fill(data, value.toByte())
		return data
	}

	private fun testCandidate(dataSource: DataSource, replacing: Boolean): UploadFiles {
		return UploadFiles( //
			context,  //
			cloudContentRepository,  //
			parent,  //
			listOf(
				UploadFile.Builder() //
					.withFileName(fileName) //
					.withDataSource(dataSource) //
					.thatIsReplacing(replacing) //
					.build()
			)
		)
	}
}
