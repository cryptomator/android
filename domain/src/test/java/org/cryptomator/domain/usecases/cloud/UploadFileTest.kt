package org.cryptomator.domain.usecases.cloud

import android.content.Context
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.repository.VaultDocumentChangeResult
import org.cryptomator.domain.repository.VaultDocumentImport
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.util.Optional
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.any as anyArg
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays
import java.util.Date

class UploadFileTest {

	private val context: Context = mock()
	private var cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private val vaultDocumentRepository: VaultDocumentRepository = mock()
	private val vaultFolder = VaultPath(Vault.aVault().withId(1L).withName("vault").withPath("/vault").withCloudType(CloudType.LOCAL).withPosition(0).build(), "folder")
	private val parent: CloudFolder = mock()
	private val targetFile: CloudFile = mock()
	private val resultFile: CloudFile = mock()

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

	@Test
	fun `files in a vault are imported and uploaded through the vault's cache`() {
		givenVaultFolder()
		val vaultImport = vaultImport { VaultDocumentChangeResult(vaultFolder.resolve(fileName), resultFile) }

		val result = testCandidate(dataSourceWithBytes(0, 3, 3), false).execute(progressAware)

		MatcherAssert.assertThat(result, CoreMatchers.`is`(listOf(resultFile)))
		verify(vaultDocumentRepository).importFile(eq(vaultFolder), eq(fileName), any(DataSource::class.java), eq(false), eq(false), eq(false), same(progressAware))
		verify(vaultImport).await()
		verifyNoMoreInteractions(cloudContentRepository)
	}

	@Test
	fun `an optimistic upload returns the file as the cache has it, without waiting for the cloud`() {
		givenVaultFolder()
		val cached: CloudFile = mock()
		val vaultImport = vaultImport { throw NetworkConnectionException() }
		whenever(vaultImport.cachedNode()).thenReturn(cached)

		val result = UploadFiles(context, cloudContentRepository, vaultDocumentRepository, parent, listOf(uploadFile(false)), true).execute(progressAware)

		MatcherAssert.assertThat(result, CoreMatchers.`is`(listOf(cached)))
		// it goes on after execute() has returned, so its progress and failure go to the listeners
		verify(vaultDocumentRepository).importFile(eq(vaultFolder), eq(fileName), any(DataSource::class.java), eq(false), eq(false), eq(true), same(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD))
		verify(vaultImport, never()).await()
	}

	@Test
	fun `an optimistic upload of a file that is gone meanwhile has no result`() {
		givenVaultFolder()
		vaultImport { throw NetworkConnectionException() }

		val result = UploadFiles(context, cloudContentRepository, vaultDocumentRepository, parent, listOf(uploadFile(false)), true).execute(progressAware)

		MatcherAssert.assertThat(result, CoreMatchers.`is`(emptyList()))
	}

	@Test
	fun `a file name taken in the vault fails the upload`() {
		givenVaultFolder()
		whenever(vaultDocumentRepository.importFile(anyArg(), anyArg(), anyOrNull(), anyArg(), anyArg(), anyArg(), anyArg())).thenThrow(CloudNodeAlreadyExistsException(fileName))

		assertThrows<CloudNodeAlreadyExistsException> { testCandidate(dataSourceWithBytes(0, 3, 3), false).execute(progressAware) }
	}

	@Test
	fun `an upload the cloud rejects fails the upload`() {
		givenVaultFolder()
		vaultImport { throw NetworkConnectionException() }

		assertThrows<NetworkConnectionException> { testCandidate(dataSourceWithBytes(0, 3, 3), false).execute(progressAware) }
	}

	@Test
	fun `canceling cancels the running import and imports no further files`() {
		givenVaultFolder()
		lateinit var inTest: UploadFiles
		val vaultImport = vaultImport {
			inTest.onCancel()
			throw java.util.concurrent.CancellationException()
		}
		inTest = UploadFiles(context, cloudContentRepository, vaultDocumentRepository, parent, listOf(uploadFile(false), uploadFile(false)), null)

		assertThrows<org.cryptomator.domain.exception.CancellationException> { inTest.execute(progressAware) }

		verify(vaultImport).cancel()
		verify(vaultDocumentRepository).importFile(anyArg(), anyArg(), anyOrNull(), anyArg(), anyArg(), anyArg(), anyArg())
	}

	@Test
	fun `canceling after the upload is done cancels nothing`() {
		givenVaultFolder()
		val vaultImport = vaultImport { VaultDocumentChangeResult(vaultFolder.resolve(fileName), resultFile) }
		val inTest = testCandidate(dataSourceWithBytes(0, 3, 3), false)
		inTest.execute(progressAware)

		inTest.onCancel()

		verify(vaultImport, never()).cancel()
	}

	@Test
	fun `files outside vaults never reach the vault's cache`() {
		val fileSize: Long = 3
		whenever(cloudContentRepository.file(parent, fileName, fileSize)).thenReturn(targetFile)
		whenever(cloudContentRepository.write(same(targetFile), any(DataSource::class.java), same(progressAware), eq(false), eq(fileSize))).thenReturn(resultFile)

		testCandidate(dataSourceWithBytes(0, fileSize, fileSize), false).execute(progressAware)

		verify(vaultDocumentRepository).pathOf(parent)
		verifyNoMoreInteractions(vaultDocumentRepository)
	}

	private fun givenVaultFolder() {
		whenever(vaultDocumentRepository.pathOf(parent)).thenReturn(vaultFolder)
	}

	private fun vaultImport(result: () -> VaultDocumentChangeResult<CloudFile>): VaultDocumentImport {
		val vaultImport: VaultDocumentImport = mock { on { await() } doAnswer { result() } }
		whenever(vaultDocumentRepository.importFile(anyArg(), anyArg(), anyOrNull(), anyArg(), anyArg(), anyArg(), anyArg())).thenReturn(vaultImport)
		return vaultImport
	}

	private fun uploadFile(replacing: Boolean): UploadFile {
		return UploadFile.Builder().withFileName(fileName).withDataSource(dataSourceWithBytes(0, 3, 3)).thatIsReplacing(replacing).build()
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

			override fun modifiedDate(context: Context): Optional<Date> {
				return Optional.of(Date())
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
			vaultDocumentRepository,  //
			parent,  //
			listOf(
				UploadFile.Builder() //
					.withFileName(fileName) //
					.withDataSource(dataSource) //
					.thatIsReplacing(replacing) //
					.build()
			), //
			null
		)
	}
}
