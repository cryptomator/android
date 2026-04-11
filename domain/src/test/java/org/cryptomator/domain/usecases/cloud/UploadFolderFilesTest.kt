package org.cryptomator.domain.usecases.cloud

import android.content.Context
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.util.Optional
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.Arrays
import java.util.Date

class UploadFolderFilesTest {

	private val context: Context = mock()
	private var cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private val parent: CloudFolder = mock()
	private val createdFolder: CloudFolder = mock()
	private val createdSubfolder: CloudFolder = mock()
	private val folderToCreate: CloudFolder = mock()
	private val subfolderToCreate: CloudFolder = mock()
	private val targetFile: CloudFile = mock()
	private val resultFile: CloudFile = mock()
	private val targetFile2: CloudFile = mock()
	private val resultFile2: CloudFile = mock()

	private val progressAware: ProgressAware<UploadState> = mock()

	private fun <T> any(type: Class<T>): T = Mockito.any(type)

	@BeforeEach
	fun setup() {
		whenever(cloudContentRepository.folder(parent, "testFolder")).thenReturn(folderToCreate)
		whenever(cloudContentRepository.create(folderToCreate)).thenReturn(createdFolder)
	}

	@Test
	@DisplayName("Upload folder with single file creates folder and uploads file")
	@Throws(BackendException::class)
	fun testUploadFolderWithSingleFile() {
		val fileSize: Long = 1337
		val dataSource = dataSourceWithBytes(0, fileSize, fileSize)

		val structure = UploadFolderStructure("testFolder")
		structure.addFile(
			UploadFile.Builder()
				.withFileName("file.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)

		whenever(cloudContentRepository.file(createdFolder, "file.txt", fileSize)).thenReturn(targetFile)
		whenever(
			cloudContentRepository.write(
				same(targetFile),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile)

		val result = inTest.execute(progressAware)

		verify(cloudContentRepository).folder(parent, "testFolder")
		verify(cloudContentRepository).create(folderToCreate)
		assertThat(result.size, `is`(1))
		assertThat(result[0], `is`(resultFile))
	}

	@Test
	@DisplayName("Upload folder with subfolder creates folders recursively")
	@Throws(BackendException::class)
	fun testUploadFolderWithSubfolder() {
		val fileSize: Long = 100
		val dataSource1 = dataSourceWithBytes(1, fileSize, fileSize)
		val dataSource2 = dataSourceWithBytes(2, fileSize, fileSize)

		val subfolder = UploadFolderStructure("sub")
		subfolder.addFile(
			UploadFile.Builder()
				.withFileName("subfile.txt")
				.withDataSource(dataSource2)
				.thatIsReplacing(false)
				.build()
		)

		val structure = UploadFolderStructure("testFolder")
		structure.addFile(
			UploadFile.Builder()
				.withFileName("rootfile.txt")
				.withDataSource(dataSource1)
				.thatIsReplacing(false)
				.build()
		)
		structure.addSubfolder(subfolder)

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)

		whenever(cloudContentRepository.folder(createdFolder, "sub")).thenReturn(subfolderToCreate)
		whenever(cloudContentRepository.create(subfolderToCreate)).thenReturn(createdSubfolder)

		whenever(cloudContentRepository.file(createdFolder, "rootfile.txt", fileSize)).thenReturn(targetFile)
		whenever(
			cloudContentRepository.write(
				same(targetFile),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile)

		whenever(cloudContentRepository.file(createdSubfolder, "subfile.txt", fileSize)).thenReturn(targetFile2)
		whenever(
			cloudContentRepository.write(
				same(targetFile2),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile2)

		val result = inTest.execute(progressAware)

		verify(cloudContentRepository).folder(parent, "testFolder")
		verify(cloudContentRepository).create(folderToCreate)
		verify(cloudContentRepository).folder(createdFolder, "sub")
		verify(cloudContentRepository).create(subfolderToCreate)
		assertThat(result.size, `is`(2))
		assertThat(result[0], `is`(resultFile))
		assertThat(result[1], `is`(resultFile2))
	}

	@Test
	@DisplayName("Upload empty folder creates folder but returns no files")
	@Throws(BackendException::class)
	fun testUploadEmptyFolder() {
		val structure = UploadFolderStructure("testFolder")

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)

		val result = inTest.execute(progressAware)

		verify(cloudContentRepository).folder(parent, "testFolder")
		verify(cloudContentRepository).create(folderToCreate)
		assertThat(result.size, `is`(0))
	}

	@Test
	@DisplayName("totalFileCount returns correct count for nested structure")
	fun testTotalFileCount() {
		val dataSource = dataSourceWithBytes(0, 10, 10)

		val deepSub = UploadFolderStructure("deep")
		deepSub.addFile(
			UploadFile.Builder()
				.withFileName("deep.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)

		val sub = UploadFolderStructure("sub")
		sub.addFile(
			UploadFile.Builder()
				.withFileName("sub1.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)
		sub.addFile(
			UploadFile.Builder()
				.withFileName("sub2.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)
		sub.addSubfolder(deepSub)

		val root = UploadFolderStructure("root")
		root.addFile(
			UploadFile.Builder()
				.withFileName("root.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)
		root.addSubfolder(sub)

		assertThat(root.totalFileCount(), `is`(4))
		assertThat(sub.totalFileCount(), `is`(3))
		assertThat(deepSub.totalFileCount(), `is`(1))
	}

	@Test
	@DisplayName("Completed files are skipped during folder upload")
	@Throws(BackendException::class)
	fun testCompletedFilesAreSkipped() {
		val fileSize: Long = 100
		val dataSource1 = dataSourceWithBytes(1, fileSize, fileSize)
		val dataSource2 = dataSourceWithBytes(2, fileSize, fileSize)

		val structure = UploadFolderStructure("testFolder")
		structure.addFile(
			UploadFile.Builder()
				.withFileName("file1.txt")
				.withDataSource(dataSource1)
				.thatIsReplacing(false)
				.build()
		)
		structure.addFile(
			UploadFile.Builder()
				.withFileName("file2.txt")
				.withDataSource(dataSource2)
				.thatIsReplacing(false)
				.build()
		)

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)
		inTest.setCompletedFiles(setOf("testFolder/file1.txt"))

		whenever(cloudContentRepository.file(createdFolder, "file2.txt", fileSize)).thenReturn(targetFile)
		whenever(
			cloudContentRepository.write(
				same(targetFile),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile)

		val result = inTest.execute(progressAware)

		assertThat(result.size, `is`(1))
		assertThat(result[0], `is`(resultFile))
	}

	@Test
	@DisplayName("FileUploadedCallback is called with relative path after each file upload")
	@Throws(BackendException::class)
	fun testFileUploadedCallbackCalledWithRelativePath() {
		val fileSize: Long = 100
		val dataSource = dataSourceWithBytes(0, fileSize, fileSize)
		val callback: FileUploadedCallback = mock()

		val structure = UploadFolderStructure("testFolder")
		structure.addFile(
			UploadFile.Builder()
				.withFileName("file.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)
		inTest.setFileUploadedCallback(callback)

		whenever(cloudContentRepository.file(createdFolder, "file.txt", fileSize)).thenReturn(targetFile)
		whenever(
			cloudContentRepository.write(
				same(targetFile),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile)

		inTest.execute(progressAware)

		verify(callback).onFileUploaded(eq("testFolder/file.txt"), same(resultFile))
	}

	@Test
	@DisplayName("Callback uses correct relative path for files in subfolders")
	@Throws(BackendException::class)
	fun testCallbackRelativePathInSubfolder() {
		val fileSize: Long = 100
		val dataSource = dataSourceWithBytes(0, fileSize, fileSize)
		val callback: FileUploadedCallback = mock()

		val subfolder = UploadFolderStructure("sub")
		subfolder.addFile(
			UploadFile.Builder()
				.withFileName("deep.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)

		val structure = UploadFolderStructure("testFolder")
		structure.addSubfolder(subfolder)

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)
		inTest.setFileUploadedCallback(callback)

		whenever(cloudContentRepository.folder(createdFolder, "sub")).thenReturn(subfolderToCreate)
		whenever(cloudContentRepository.create(subfolderToCreate)).thenReturn(createdSubfolder)
		whenever(cloudContentRepository.file(createdSubfolder, "deep.txt", fileSize)).thenReturn(targetFile)
		whenever(
			cloudContentRepository.write(
				same(targetFile),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile)

		inTest.execute(progressAware)

		verify(callback).onFileUploaded(eq("testFolder/sub/deep.txt"), same(resultFile))
	}

	@Test
	@DisplayName("createFolderSafe returns existing folder when CloudNodeAlreadyExistsException is thrown")
	@Throws(BackendException::class)
	fun testCreateFolderSafeHandlesAlreadyExists() {
		val fileSize: Long = 100
		val dataSource = dataSourceWithBytes(0, fileSize, fileSize)

		val structure = UploadFolderStructure("testFolder")
		structure.addFile(
			UploadFile.Builder()
				.withFileName("file.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)

		whenever(cloudContentRepository.create(folderToCreate)).thenThrow(CloudNodeAlreadyExistsException("testFolder"))

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)

		whenever(cloudContentRepository.file(folderToCreate, "file.txt", fileSize)).thenReturn(targetFile)
		whenever(
			cloudContentRepository.write(
				same(targetFile),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile)

		val result = inTest.execute(progressAware)

		assertThat(result.size, `is`(1))
		assertThat(result[0], `is`(resultFile))
	}

	@Test
	@DisplayName("Completed files in subfolder are skipped correctly")
	@Throws(BackendException::class)
	fun testCompletedFilesInSubfolderAreSkipped() {
		val fileSize: Long = 100
		val dataSource1 = dataSourceWithBytes(1, fileSize, fileSize)
		val dataSource2 = dataSourceWithBytes(2, fileSize, fileSize)

		val subfolder = UploadFolderStructure("sub")
		subfolder.addFile(
			UploadFile.Builder()
				.withFileName("subfile1.txt")
				.withDataSource(dataSource1)
				.thatIsReplacing(false)
				.build()
		)
		subfolder.addFile(
			UploadFile.Builder()
				.withFileName("subfile2.txt")
				.withDataSource(dataSource2)
				.thatIsReplacing(false)
				.build()
		)

		val structure = UploadFolderStructure("testFolder")
		structure.addSubfolder(subfolder)

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)
		inTest.setCompletedFiles(setOf("testFolder/sub/subfile1.txt"))

		whenever(cloudContentRepository.folder(createdFolder, "sub")).thenReturn(subfolderToCreate)
		whenever(cloudContentRepository.create(subfolderToCreate)).thenReturn(createdSubfolder)
		whenever(cloudContentRepository.file(createdSubfolder, "subfile2.txt", fileSize)).thenReturn(targetFile2)
		whenever(
			cloudContentRepository.write(
				same(targetFile2),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile2)

		val result = inTest.execute(progressAware)

		assertThat(result.size, `is`(1))
		assertThat(result[0], `is`(resultFile2))
	}

	@Test
	@DisplayName("Per-file CloudNodeAlreadyExistsException is caught and file is skipped")
	@Throws(BackendException::class)
	fun testPerFileAlreadyExistsIsSkipped() {
		val fileSize: Long = 100
		val dataSource1 = dataSourceWithBytes(1, fileSize, fileSize)
		val dataSource2 = dataSourceWithBytes(2, fileSize, fileSize)
		val callback: FileUploadedCallback = mock()

		val structure = UploadFolderStructure("testFolder")
		structure.addFile(
			UploadFile.Builder()
				.withFileName("existing.txt")
				.withDataSource(dataSource1)
				.thatIsReplacing(false)
				.build()
		)
		structure.addFile(
			UploadFile.Builder()
				.withFileName("new.txt")
				.withDataSource(dataSource2)
				.thatIsReplacing(false)
				.build()
		)

		val inTest = UploadFolderFiles(context, cloudContentRepository, parent, structure)
		inTest.setFileUploadedCallback(callback)

		val existingTarget: CloudFile = mock()
		whenever(cloudContentRepository.file(createdFolder, "existing.txt", fileSize)).thenReturn(existingTarget)
		whenever(
			cloudContentRepository.write(
				same(existingTarget),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenThrow(CloudNodeAlreadyExistsException("existing.txt"))

		whenever(cloudContentRepository.file(createdFolder, "new.txt", fileSize)).thenReturn(targetFile)
		whenever(
			cloudContentRepository.write(
				same(targetFile),
				any(DataSource::class.java),
				same(progressAware),
				eq(false),
				eq(fileSize)
			)
		).thenReturn(resultFile)

		val result = inTest.execute(progressAware)

		assertThat(result.size, `is`(1))
		assertThat(result[0], `is`(resultFile))
		verify(callback).onFileUploaded(eq("testFolder/existing.txt"), eq(null))
		verify(callback).onFileUploaded(eq("testFolder/new.txt"), same(resultFile))
	}

	@Test
	@DisplayName("setAllReplacing recursively sets replacing on all files")
	fun testSetAllReplacing() {
		val dataSource = dataSourceWithBytes(0, 10, 10)

		val subfolder = UploadFolderStructure("sub")
		subfolder.addFile(
			UploadFile.Builder()
				.withFileName("subfile.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)

		val root = UploadFolderStructure("root")
		root.addFile(
			UploadFile.Builder()
				.withFileName("rootfile.txt")
				.withDataSource(dataSource)
				.thatIsReplacing(false)
				.build()
		)
		root.addSubfolder(subfolder)

		root.setAllReplacing(true)

		assertThat(root.files[0].replacing, `is`(true))
		assertThat(root.subfolders[0].files[0].replacing, `is`(true))
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
}
