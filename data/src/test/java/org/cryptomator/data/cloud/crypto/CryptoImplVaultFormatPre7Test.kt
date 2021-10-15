package org.cryptomator.data.cloud.crypto

import android.content.Context
import com.google.common.base.Strings
import com.google.common.io.BaseEncoding
import org.apache.commons.codec.binary.Base32
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.api.FileContentCryptor
import org.cryptomator.cryptolib.api.FileHeader
import org.cryptomator.cryptolib.api.FileHeaderCryptor
import org.cryptomator.cryptolib.api.FileNameCryptor
import org.cryptomator.cryptolib.common.MessageDigestSupplier
import org.cryptomator.data.cloud.crypto.DirIdCache.DirIdInfo
import org.cryptomator.data.util.CopyStream.copyStreamToStream
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource.Companion.from
import org.cryptomator.domain.usecases.cloud.DataSource
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.AdditionalMatchers
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.function.Supplier
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting

/**
 * `
 * path/to/vault/d/00
 * ├─ Directory 1
 * │  ├─ Directory 2
 * │  ├─ Directory 3x250
 * │  │  ├─ Directory 4x250
 * │  │  └─ File 5x250
 * │  └─ File 3
 * ├─ File 1
 * ├─ File 2
 * ├─ File 4
` *
 */
internal class CryptoImplVaultFormatPre7Test {

	private var context: Context = mock()
	private var cloud: Cloud = mock()
	private var cryptoCloud: CryptoCloud = mock()
	private var cryptor: Cryptor = mock()
	private var cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private var dirIdCache: DirIdCache = mock()
	private var fileNameCryptor: FileNameCryptor = mock()
	private var fileContentCryptor: FileContentCryptor = mock()
	private var fileHeaderCryptor: FileHeaderCryptor = mock()
	private var tmpDir = createTempDirectory()

	private val dirIdRoot = ""
	private val dirId1 = "dir1-id"
	private val dirId2 = "dir2-id"
	private val rootFolder: TestFolder = RootTestFolder(cloud)
	private val d = TestFolder(rootFolder, "d", "/d")
	private val m = TestFolder(rootFolder, "m", "/m")
	private var lvl2Dir = TestFolder(d, "00", "/d/00")
	private val aaFolder = TestFolder(lvl2Dir, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")

	private lateinit var inTest: CryptoImplVaultFormatPre7
	private lateinit var root: RootCryptoFolder
	private lateinit var cryptoFile1: CryptoFile
	private lateinit var cryptoFile2: CryptoFile
	private lateinit var cryptoFile4: CryptoFile
	private lateinit var cryptoFolder1: CryptoFolder

	private fun <T> any(type: Class<T>): T = Mockito.any(type)

	@BeforeEach
	@Throws(BackendException::class)
	fun setup() {
		whenever(context.cacheDir).thenReturn(tmpDir.toFile())

		whenever(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor)
		whenever(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor)
		whenever(cryptor.fileContentCryptor()).thenReturn(fileContentCryptor)
		whenever(cryptor.fileHeaderCryptor()).thenReturn(fileHeaderCryptor)

		root = RootCryptoFolder(cryptoCloud)
		inTest = CryptoImplVaultFormatPre7(context, Supplier { cryptor }, cloudContentRepository, rootFolder, dirIdCache)

		whenever(fileNameCryptor.hashDirectoryId(dirIdRoot)).thenReturn("00AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
		whenever(fileNameCryptor.hashDirectoryId(dirId1)).thenReturn("11BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		whenever(fileNameCryptor.hashDirectoryId(dirId2)).thenReturn("22CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), "dir1", dirIdRoot.toByteArray())).thenReturn("Directory 1")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), "file1", dirIdRoot.toByteArray())).thenReturn("File 1")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), "file2", dirIdRoot.toByteArray())).thenReturn("File 2")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), "dir2", dirId1.toByteArray())).thenReturn("Directory 2")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), "file3", dirId1.toByteArray())).thenReturn("File 3")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), "file4", dirIdRoot.toByteArray())).thenReturn("File 4")

		val testFile1 = TestFile(aaFolder, "file1.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file1.c9r", null, null)
		val testFile2 = TestFile(aaFolder, "file2.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file2.c9r", null, null)
		val testFile4 = TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", null, null)
		val testDir1 = TestFile(aaFolder, "0dir1", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/0dir1", null, null)
		val rootItems: ArrayList<CloudNode> = object : ArrayList<CloudNode>() {
			init {
				add(testFile1)
				add(testFile2)
				add(testFile4)
				add(testDir1)
			}
		}
		cryptoFile1 = CryptoFile(root, "File 1", "/File 1", 15L, testFile1)
		cryptoFile2 = CryptoFile(root, "File 2", "/File 2", null, testFile2)
		cryptoFile4 = CryptoFile(root, "File 4", "/File 4", null, testFile4)
		cryptoFolder1 = CryptoFolder(root, "Directory 1", "/Directory 1", testDir1)

		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "00")).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.folder(lvl2Dir, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).thenReturn(aaFolder)
		whenever(cloudContentRepository.file(aaFolder, "0dir1")).thenReturn(testDir1)
		whenever(cloudContentRepository.exists(testDir1)).thenReturn(true)
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(dirId1.toByteArray()), out)
			null
		}.`when`(cloudContentRepository).read(eq(cryptoFolder1.dirFile!!), any(), any(), any())
		whenever<List<*>>(cloudContentRepository.list(aaFolder)).thenReturn(rootItems)
		whenever(dirIdCache.put(eq(root), any())).thenReturn(DirIdInfo("", aaFolder))
	}

	@AfterEach
	fun tearDown() {
		tmpDir.deleteExisting()
	}

	@Test
	@DisplayName("list(\"/\")")
	@Throws(BackendException::class)
	fun testListRoot() {
		val rootDirContent = inTest.list(root)

		Matchers.contains(rootDirContent, cryptoFile1)
		Matchers.contains(rootDirContent, cryptoFile2)
		Matchers.contains(rootDirContent, cryptoFile4)
		Matchers.contains(rootDirContent, cryptoFolder1)
	}

	@Test
	@DisplayName("list(\"/Directory 1/Directory 3x250\")")
	@Throws(BackendException::class)
	fun testListDirectory3x250() {
		val dir3Name = "Directory " + Strings.repeat("3", 250)
		val dir3Cipher = "dir" + Strings.repeat("3", 250)
		val longFilenameBytes = "$dir3Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base32().encode(hash) + ".c9s"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val ddLvl2Dir = TestFolder(d, "33", "/d/33")
		val ddFolder = TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		val testDir3 = TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName")
		val testDir3DirFile = TestFile(testDir3, "dir.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/dir.c9r", null, null)
		val testDir3NameFile = TestFile(testDir3, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/name.c9s", null, null)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), dir3Name, dirId1.toByteArray())).thenReturn(dir3Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), dir3Cipher, dirId1.toByteArray())).thenReturn(dir3Name)
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile)
		whenever(cloudContentRepository.file(testDir3, "name.c9s")).thenReturn(testDir3NameFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "Directory 1", dirIdRoot.toByteArray())).thenReturn("dir1")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "Directory 2", dirId1.toByteArray())).thenReturn("dir2")

		val cryptoFolder3 = CryptoFolder(cryptoFolder1, dir3Name, "/Directory 1/$dir3Name", testDir3DirFile)

		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream("dir3-id".toByteArray()), out)
			null
		}.`when`(cloudContentRepository).read(eq(cryptoFolder3.dirFile!!), any(), any(), any())

		/*
		 * │ ├─ Directory 3x250
		 * │ │ ├─ Directory 4x250
		 * │ │ └─ File 5x250
		 */
		val dir4Name = "Directory " + Strings.repeat("4", 250)
		val dir4Cipher = "dir" + Strings.repeat("4", 250)
		val longFilenameBytes4 = "$dir4Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash4 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes4)
		val shortenedFileName4 = BaseEncoding.base32().encode(hash4) + ".c9s"
		val directory4x250 = TestFolder(ddFolder, shortenedFileName4, "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD$shortenedFileName4")
		val testDir4DirFile = TestFile(directory4x250, "dir.c9r", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/$shortenedFileName4/dir.c9r", null, null)
		val testDir4NameFile = TestFile(directory4x250, "name.c9s", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/$shortenedFileName4/name.c9s", null, null)

		whenever(cloudContentRepository.file(directory4x250, "dir.c9r")).thenReturn(testDir4DirFile)
		whenever(cloudContentRepository.file(directory4x250, "name.c9s")).thenReturn(testDir4NameFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), dir4Name, "dir3-id".toByteArray())).thenReturn(dir4Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), dir4Cipher, "dir3-id".toByteArray())).thenReturn(dir4Name)
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(dir4Cipher.toByteArray(charset("UTF-8"))), out)
			null
		}.`when`(cloudContentRepository).read(eq(testDir4NameFile), any(), any(), any())

		val dir4Files: ArrayList<CloudNode> = object : ArrayList<CloudNode>() {
			init {
				add(testDir4DirFile)
				add(testDir4NameFile)
			}
		}
		val file5Name = "File " + Strings.repeat("5", 250)
		val file5Cipher = "file" + Strings.repeat("5", 250)
		val longFilenameBytes5 = "$file5Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash5 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes5)
		val shortenedFileName5 = BaseEncoding.base32().encode(hash5) + ".c9s"
		val directory5x250 = TestFolder(ddFolder, shortenedFileName5, "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD$shortenedFileName5")
		val testFile5ContentFile = TestFile(directory5x250, "contents.c9r", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/$shortenedFileName5/contents.c9r", null, null)
		val testFile5NameFile = TestFile(directory5x250, "name.c9s", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/$shortenedFileName5/name.c9s", null, null)

		whenever(cloudContentRepository.file(directory5x250, "contents.c9r")).thenReturn(testFile5ContentFile)
		whenever(cloudContentRepository.file(directory5x250, "name.c9s")).thenReturn(testFile5NameFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), file5Name, "dir3-id".toByteArray())).thenReturn(file5Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), file5Cipher, "dir3-id".toByteArray())).thenReturn(file5Name)
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(file5Cipher.toByteArray(charset("UTF-8"))), out)
			null
		}.`when`(cloudContentRepository).read(eq(testFile5NameFile), any(), any(), any())
		val dir5Files: ArrayList<CloudNode> = object : ArrayList<CloudNode>() {
			init {
				add(testFile5ContentFile)
				add(testFile5NameFile)
			}
		}
		val dir3Items: ArrayList<CloudNode> = object : ArrayList<CloudNode>() {
			init {
				add(directory4x250)
				add(directory5x250)
			}
		}
		whenever(cloudContentRepository.exists(testDir3DirFile)).thenReturn(true)
		whenever<List<*>>(cloudContentRepository.list(ddFolder)).thenReturn(dir3Items)
		whenever<List<*>>(cloudContentRepository.list(directory4x250)).thenReturn(dir4Files)
		whenever<List<*>>(cloudContentRepository.list(directory5x250)).thenReturn(dir5Files)
		whenever(dirIdCache.put(eq(cryptoFolder3), any())).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(dirIdCache[cryptoFolder3]).thenReturn(DirIdInfo("dir3-id", ddFolder))

		val folder3Content = inTest.list(cryptoFolder3)

		Matchers.contains(folder3Content, CryptoFolder(cryptoFolder3, dir4Name, "/Directory 1/$dir3Name/$dir4Name", testDir4DirFile))
		Matchers.contains(folder3Content, CryptoFile(cryptoFolder3, file5Name, "/Directory 1/$dir3Name/$file5Name", null, testFile5ContentFile))
	}

	@Test
	@DisplayName("read(\"/File 1\", NO_PROGRESS_AWARE)")
	@Throws(BackendException::class)
	fun testReadFromShortFile() {
		val file1Content = "hhhhhTOPSECRET!TOPSECRET!TOPSECRET!TOPSECRET!".toByteArray()
		val header: FileHeader = mock()

		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(8)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "File 1", dirIdRoot.toByteArray())).thenReturn("file1")
		whenever(fileHeaderCryptor.decryptHeader(StandardCharsets.UTF_8.encode("hhhhh"))).thenReturn(header)
		whenever(fileContentCryptor.decryptChunk(eq(StandardCharsets.UTF_8.encode("TOPSECRET!")), any(), eq(header), any()))
			.then { invocation: InvocationOnMock? -> StandardCharsets.UTF_8.encode("geheim!!") }
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(file1Content), out)
			null
		}.`when`(cloudContentRepository).read(eq(cryptoFile1.cloudFile), any(), any(), any())

		val outputStream = ByteArrayOutputStream(1000)
		inTest.read(cryptoFile1, outputStream, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)

		MatcherAssert.assertThat(outputStream.toString(), CoreMatchers.`is`("geheim!!geheim!!geheim!!geheim!!"))
	}

	@Test
	@DisplayName("read(\"/File 15x250\", NO_PROGRESS_AWARE)")
	@Throws(BackendException::class)
	fun testReadFromLongFile() {
		val file3Name = "File " + Strings.repeat("15", 250)
		val longFilenameBytes = file3Name.toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base32().encode(hash) + ".c9s"
		val testFile3Folder = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testFile3ContentFile = TestFile(testFile3Folder, "content.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/content.c9r", null, null)
		val file1Content = "hhhhhTOPSECRET!TOPSECRET!TOPSECRET!TOPSECRET!".toByteArray()
		val header: FileHeader = mock()

		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(8)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileHeaderCryptor.decryptHeader(StandardCharsets.UTF_8.encode("hhhhh"))).thenReturn(header)
		whenever(fileContentCryptor.decryptChunk(eq(StandardCharsets.UTF_8.encode("TOPSECRET!")), any(), eq(header), any()))
			.then { invocation: InvocationOnMock? -> StandardCharsets.UTF_8.encode("geheim!!") }
		val cryptoFile15 = CryptoFile(root, file3Name, "/$file3Name", null, testFile3ContentFile)
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(file1Content), out)
			null
		}.`when`(cloudContentRepository).read(eq(cryptoFile15.cloudFile), any(), any(), any())

		val outputStream = ByteArrayOutputStream(1000)
		inTest.read(cryptoFile15, outputStream, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)

		MatcherAssert.assertThat(outputStream.toString(), CoreMatchers.`is`("geheim!!geheim!!geheim!!geheim!!"))
	}

	@Test
	@DisplayName("write(\"/File 1\", text, NO_PROGRESS_AWARE, replace=false, 10bytes)")
	@Throws(BackendException::class)
	fun testWriteToShortFile() {
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "File 1", dirIdRoot.toByteArray())).thenReturn("file1")
		val header: FileHeader = mock()
		whenever(fileHeaderCryptor.create()).thenReturn(header)
		whenever(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap("hhhhh".toByteArray()))
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(10)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(
			fileContentCryptor.encryptChunk(
				any(ByteBuffer::class.java), any(), any(
					FileHeader::class.java
				)
			)
		).thenAnswer { invocation: InvocationOnMock ->
			val input = invocation.getArgument<ByteBuffer>(0)
			val inStr = StandardCharsets.UTF_8.decode(input).toString()
			ByteBuffer.wrap(inStr.lowercase().toByteArray(StandardCharsets.UTF_8))
		}
		whenever(cloudContentRepository.write(eq(cryptoFile1.cloudFile), any(DataSource::class.java), any(), eq(false), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val encrypted = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
				MatcherAssert.assertThat(encrypted, CoreMatchers.`is`("hhhhhtopsecret!"))
				invocationOnMock.getArgument(0)
			}

		// just for the exists check
		whenever(cloudContentRepository.file(aaFolder, "0file1")).thenReturn(TestFile(rootFolder, "0file1", aaFolder.path + "0file1", null, null))

		val cryptoFile = inTest.write(cryptoFile1, from("TOPSECRET!".toByteArray(StandardCharsets.UTF_8)), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 10L)

		MatcherAssert.assertThat(cryptoFile, CoreMatchers.`is`(cryptoFile1))
	}

	@Test
	@DisplayName("write(\"/File 15x250\", text, NO_PROGRESS_AWARE, replace=false, 10bytes)")
	@Throws(BackendException::class)
	fun testWriteToLongFile() {
		val file15Name = "File " + Strings.repeat("15", 250)
		val file15Cipher = "file" + Strings.repeat("15", 250)
		val longFilenameBytes = file15Cipher.toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = Base32().encodeAsString(hash) + ".lng"
		val metaDataDFile: CloudFile = TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName", null, null)
		val metaDataMFile = metadataFile(shortenedFileName)
		val cryptoFile15 = CryptoFile(root, file15Name, "/$file15Name", 15L, metaDataDFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), file15Name, dirIdRoot.toByteArray())).thenReturn(file15Cipher)
		val header: FileHeader = mock()
		whenever(fileHeaderCryptor.create()).thenReturn(header)
		whenever(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap("hhhhh".toByteArray()))
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(10)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(
			fileContentCryptor.encryptChunk(
				any(ByteBuffer::class.java), any(), any(
					FileHeader::class.java
				)
			)
		).thenAnswer { invocation: InvocationOnMock ->
			val input = invocation.getArgument<ByteBuffer>(0)
			val inStr = StandardCharsets.UTF_8.decode(input).toString()
			ByteBuffer.wrap(inStr.lowercase().toByteArray(StandardCharsets.UTF_8))
		}
		whenever(cloudContentRepository.write(eq(metaDataDFile), any(DataSource::class.java), any(), eq(false), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val encrypted = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
				MatcherAssert.assertThat(encrypted, CoreMatchers.`is`("hhhhhtopsecret!"))
				invocationOnMock.getArgument(0)
			}
		whenever(cloudContentRepository.write(eq(metaDataMFile), any(DataSource::class.java), any(), eq(true), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val encrypted = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
				MatcherAssert.assertThat(encrypted, CoreMatchers.`is`(file15Cipher))
				invocationOnMock.getArgument(0)
			}

		// just for the exists check
		whenever(cloudContentRepository.file(aaFolder, shortenedFileName, null)).thenReturn(metaDataDFile)
		val file15Cipher0 = "0file" + Strings.repeat("15", 250)
		val longFilenameBytes1 = file15Cipher0.toByteArray(StandardCharsets.UTF_8)
		val hash1 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes1)
		val shortenedFileName1 = Base32().encodeAsString(hash1) + ".lng"
		val metaDataMFile1 = metadataFile(shortenedFileName1)

		whenever(cloudContentRepository.file(aaFolder, shortenedFileName1)).thenReturn(metaDataMFile1)

		val res = inTest.file(root, file15Name)

		val cryptoFile = inTest.write(cryptoFile15, from("TOPSECRET!".toByteArray(StandardCharsets.UTF_8)), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 10L)

		MatcherAssert.assertThat(cryptoFile, CoreMatchers.`is`(cryptoFile15))
		Mockito.verify(cloudContentRepository).write(
			eq(metaDataDFile), any(
				DataSource::class.java
			), any(), eq(false), any()
		)
		Mockito.verify(cloudContentRepository).write(
			eq(metaDataMFile), any(
				DataSource::class.java
			), any(), eq(true), any()
		)
	}

	@Test
	@DisplayName("create(\"/Directory 3/\")")
	@Throws(BackendException::class)
	fun testCreateShortFolder() {
		/*
		 * <code>
		 * path/to/vault/d
		 * ├─ Directory 1
		 * │ ├─ ...
		 * ├─ Directory 3
		 * ├─ ...
		 * </code>
		 */
		lvl2Dir = TestFolder(d, "33", "/d/33")
		val ddFolder = TestFolder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		val testDir3DirFile = TestFile(aaFolder, "0dir3", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/0dir3", null, null)
		val cryptoFolder3 = CryptoFolder(root, "Directory 3", "/Directory 3", testDir3DirFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "Directory 3", dirIdRoot.toByteArray())).thenReturn("dir3")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), "dir3", dirIdRoot.toByteArray())).thenReturn("Directory 3")
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(cloudContentRepository.file(aaFolder, "0dir3")).thenReturn(testDir3DirFile)
		whenever(dirIdCache.put(eq(cryptoFolder3), any())).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(dirIdCache[cryptoFolder3]).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(cloudContentRepository.create(lvl2Dir)).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.create(ddFolder)).thenReturn(ddFolder)
		whenever(cloudContentRepository.write(eq(testDir3DirFile), any(), any(), eq(false), any())).thenReturn(testDir3DirFile)

		// just for the exists check
		whenever(cloudContentRepository.file(aaFolder, "dir3", null)).thenReturn(TestFile(aaFolder, "dir3", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3", null, null))

		val cloudFolder: CloudFolder = inTest.create(cryptoFolder3)

		MatcherAssert.assertThat(cloudFolder, CoreMatchers.`is`(cryptoFolder3))
		Mockito.verify(cloudContentRepository).create(ddFolder)
		Mockito.verify(cloudContentRepository).write(eq(testDir3DirFile), any(), any(), eq(false), any())
	}

	@Test
	@DisplayName("create(\"/Directory 3x250/\")")
	@Throws(BackendException::class)
	fun testCreateLongFolder() {
		/*
		 * <code>
		 * path/to/vault/d
		 * ├─ Directory 1
		 * │ ├─ ...
		 * ├─ Directory 3x250
		 * ├─ ...
		 * </code>
		 */
		val dir3Name = "Directory " + Strings.repeat("3", 250)
		val dir3Cipher = "dir" + Strings.repeat("3", 250)
		val longFilenameBytes = "0$dir3Cipher".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = Base32().encodeAsString(hash) + ".lng"
		val ddLvl2Dir = TestFolder(d, "33", "/d/33")
		val ddFolder = TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		val testDir3DirFile = TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName", null, null)
		val testDir3NameFile = metadataFile(shortenedFileName)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), dir3Name, dirIdRoot.toByteArray())).thenReturn(dir3Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), dir3Cipher, dirIdRoot.toByteArray())).thenReturn(dir3Name)
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)

		val cryptoFolder3 = CryptoFolder(root, dir3Name, "/$dir3Name", testDir3DirFile)
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(dirIdCache.put(eq(cryptoFolder3), any())).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(cloudContentRepository.create(ddLvl2Dir)).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.create(ddFolder)).thenReturn(ddFolder)
		whenever(cloudContentRepository.create(testDir3NameFile.parent!!)).thenReturn(testDir3NameFile.parent!!)
		whenever(cloudContentRepository.write(eq(testDir3DirFile), any(), any(), eq(false), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("dir3-id"))
			testDir3DirFile
		}
		whenever(cloudContentRepository.write(eq(testDir3NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val nameContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(nameContent, CoreMatchers.`is`("0$dir3Cipher"))
			testDir3NameFile
		}

		// just for the exists check
		whenever(cloudContentRepository.file(aaFolder, shortenedFileName)).thenReturn(TestFile(aaFolder, shortenedFileName, aaFolder.path + "/" + shortenedFileName, null, null))

		var cloudFolder = inTest.folder(root, dir3Name)

		val longFilenameBytes1 = dir3Cipher.toByteArray(StandardCharsets.UTF_8)
		val hash1 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes1)
		val shortenedFileName1 = Base32().encodeAsString(hash1) + ".lng"
		val testDir3NameFile1 = metadataFile(shortenedFileName1)

		whenever(cloudContentRepository.file(aaFolder, shortenedFileName1, null)).thenReturn(testDir3NameFile1)

		cloudFolder = inTest.create(cryptoFolder3)

		MatcherAssert.assertThat(cloudFolder, CoreMatchers.`is`(cryptoFolder3))
		Mockito.verify(cloudContentRepository).create(ddFolder)
		Mockito.verify(cloudContentRepository).create(testDir3NameFile.parent!!)
		Mockito.verify(cloudContentRepository).write(eq(testDir3DirFile), any(), any(), eq(false), any())
		Mockito.verify(cloudContentRepository).write(eq(testDir3NameFile), any(), any(), eq(true), any())
	}

	@Test
	@DisplayName("delete(\"/File 4\")")
	@Throws(BackendException::class)
	fun testDeleteShortFile() {
		inTest.delete(cryptoFile4)
		Mockito.verify(cloudContentRepository).delete(cryptoFile4.cloudFile)
	}

	@Test
	@DisplayName("delete(\"/File 15x250\")")
	@Throws(BackendException::class)
	fun testDeleteLongFile() {
		val file15Name = "File " + Strings.repeat("15", 250)
		val file15Cipher = "file" + Strings.repeat("15", 250)
		val longFilenameBytes = file15Name.toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = Base32().encodeAsString(hash) + ".lng"
		val metaDataFile = metadataFile(shortenedFileName)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), file15Name, dirIdRoot.toByteArray())).thenReturn(file15Cipher)
		val cryptoFile15 = CryptoFile(root, file15Name, "/$file15Name", 15L, metaDataFile)

		inTest.delete(cryptoFile15)
		Mockito.verify(cloudContentRepository).delete(metaDataFile)
	}

	@Test
	@DisplayName("delete(\"/Directory 1/Directory 2/\")")
	@Throws(BackendException::class)
	fun testDeleteSingleShortFolder() {
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val ccLvl2Dir = TestFolder(d, "22", "/d/22")
		val ccFolder = TestFolder(ccLvl2Dir, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC", "/d/22/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "Directory 1", dirIdRoot.toByteArray())).thenReturn("dir1")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "Directory 2", dirId1.toByteArray())).thenReturn("dir2")

		val testDir2DirFile = TestFile(bbFolder, "0dir2", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/0dir2", null, null)
		val cryptoFolder2 = CryptoFolder(cryptoFolder1, "Directory 2", "/Directory 1/Directory 2", testDir2DirFile)

		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(dirId2.toByteArray()), out)
			null
		}.`when`(cloudContentRepository).read(eq(cryptoFolder2.dirFile!!), any(), any(), any())

		val dir1Items: ArrayList<CloudNode> = object : ArrayList<CloudNode>() {
			init {
				add(testDir2DirFile)
			}
		}

		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "22")).thenReturn(ccLvl2Dir)
		whenever(cloudContentRepository.folder(ccLvl2Dir, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")).thenReturn(ccFolder)
		whenever(cloudContentRepository.file(aaFolder, "0dir2")).thenReturn(testDir2DirFile)
		whenever<List<*>>(cloudContentRepository.list(bbFolder)).thenReturn(dir1Items)
		whenever(dirIdCache.put(eq(cryptoFolder2), any())).thenReturn(DirIdInfo(dirId2, ccFolder))
		whenever(dirIdCache[cryptoFolder2]).thenReturn(DirIdInfo(dirId2, ccFolder))
		whenever(cloudContentRepository.exists(testDir2DirFile)).thenReturn(true)
		whenever<List<*>>(cloudContentRepository.list(ccFolder)).thenReturn(ArrayList<CloudNode>())

		inTest.delete(cryptoFolder2)

		Mockito.verify(cloudContentRepository).delete(ccFolder)
		Mockito.verify(cloudContentRepository).delete(testDir2DirFile)
		Mockito.verify(dirIdCache).evict(cryptoFolder2)
	}

	@Test
	@DisplayName("delete(\"/Directory 3x250\")")
	@Throws(BackendException::class)
	fun testDeleteSingleLongFolder() {
		/*
		 * <code>
		 * path/to/vault/d
		 * ├─ Directory 1
		 * │ ├─ ...
		 * ├─ Directory 3x250
		 * ├─ ...
		 * </code>
		 */
		val dir3Name = "Directory " + Strings.repeat("3", 250)
		val dir3Cipher = "dir" + Strings.repeat("3", 250)
		val longFilenameBytes = "0$dir3Cipher".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = Base32().encodeAsString(hash) + ".lng"
		val ddLvl2Dir = TestFolder(d, "33", "/d/33")
		val ddFolder = TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		val testDir3DirFile = TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName", null, null)
		val testDir3NameFile = metadataFile(shortenedFileName)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), dir3Name, dirIdRoot.toByteArray())).thenReturn(dir3Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), dir3Cipher, dirIdRoot.toByteArray())).thenReturn(dir3Name)
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)

		val cryptoFolder3 = CryptoFolder(root, dir3Name, "/$dir3Name", testDir3DirFile)

		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(dirIdCache.put(eq(cryptoFolder3), any())).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(cloudContentRepository.file(aaFolder, shortenedFileName)).thenReturn(testDir3DirFile)
		whenever(cloudContentRepository.file(testDir3NameFile.parent!!, shortenedFileName, 257L)).thenReturn(testDir3NameFile)
		whenever<List<*>>(cloudContentRepository.list(ddFolder)).thenReturn(ArrayList<CloudNode>())

		inTest.delete(cryptoFolder3)

		Mockito.verify(cloudContentRepository).delete(ddFolder)
		Mockito.verify(cloudContentRepository).delete(testDir3DirFile)
		Mockito.verify(dirIdCache).evict(cryptoFolder3)
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4\")")
	@Throws(BackendException::class)
	fun testMoveShortFileToNewShortFile() {
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testFile4 = TestFile(aaFolder, "file4", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4", null, null)
		val testMovedFile4 = TestFile(bbFolder, "file4", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file4", null, null)
		val cryptoFile4 = CryptoFile(root, "File 4", "/File 4", null, testFile4)
		val cryptoMovedFile4 = CryptoFile(cryptoFolder1, "File 4", "/Directory 1/File 4", null, testMovedFile4)

		whenever(cloudContentRepository.file(aaFolder, "file4")).thenReturn(testFile4)
		whenever(cloudContentRepository.file(bbFolder, "file4")).thenReturn(testMovedFile4)
		whenever(cloudContentRepository.move(testFile4, testMovedFile4)).thenReturn(testMovedFile4)
		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(cloudContentRepository.folder(bbFolder, "file4")).thenReturn(null)
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "File 4", dirId1.toByteArray())).thenReturn("file4")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "File 4", dirIdRoot.toByteArray())).thenReturn("file4")

		// just for the exists check
		whenever(cloudContentRepository.file(bbFolder, "0file4")).thenReturn(TestFile(bbFolder, "0file4", bbFolder.path + "/0file4", null, null))

		val result = inTest.move(cryptoFile4, cryptoMovedFile4)

		Assertions.assertEquals("File 4", result.name)
		Mockito.verify(cloudContentRepository).move(testFile4, testMovedFile4)
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4x250\")")
	@Throws(BackendException::class)
	fun testMoveShortFileToNewLongFile() {
		val file4Name = "File " + Strings.repeat("4", 250)
		val file4Cipher = "file" + Strings.repeat("4", 250)
		val longFilenameBytes = file4Cipher.toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base32().encode(hash) + ".lng"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testFile4 = TestFile(aaFolder, "file4", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4", null, null)
		val cryptoFile4 = CryptoFile(root, "File 4", "/File 4", null, testFile4)
		val testFile4ContentFile = TestFile(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName", null, null)
		val testFile4NameFile = metadataFile(shortenedFileName)
		val cryptoMovedFile4 = CryptoFile(cryptoFolder1, file4Name, "/Directory 1/$file4Name", null, testFile4ContentFile)

		whenever(cloudContentRepository.move(testFile4, testFile4ContentFile)).thenReturn(testFile4ContentFile)
		whenever(cloudContentRepository.create(testFile4NameFile.parent!!)).thenReturn(testFile4NameFile.parent)
		whenever(cloudContentRepository.write(eq(testFile4NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`(file4Cipher))
			testFile4NameFile
		}
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "File 4", dirIdRoot.toByteArray())).thenReturn("file4")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), file4Name, dirId1.toByteArray())).thenReturn(file4Cipher)

		// just for the exists check
		whenever(cloudContentRepository.file(bbFolder, shortenedFileName, null)).thenReturn(testFile4ContentFile)
		whenever(cloudContentRepository.create(testFile4NameFile.parent!!)).thenReturn(testFile4NameFile.parent!!)
		val file4Cipher0 = "0file" + Strings.repeat("4", 250)
		val longFilenameBytes0 = file4Cipher0.toByteArray(StandardCharsets.UTF_8)
		val hash0 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes0)
		val shortenedFileName0 = BaseEncoding.base32().encode(hash0) + ".lng"
		val testFile4NameFile0 = metadataFile(shortenedFileName0)
		whenever(cloudContentRepository.file(bbFolder, shortenedFileName0)).thenReturn(testFile4NameFile0)

		val targetFile = inTest.file(cryptoFolder1, file4Name) // needed due to ugly side effect
		val result = inTest.move(cryptoFile4, cryptoMovedFile4)

		Assertions.assertEquals(file4Name, result.name)
		Mockito.verify(cloudContentRepository).create(testFile4NameFile.parent!!)
		Mockito.verify(cloudContentRepository).move(testFile4, testFile4ContentFile)
		Mockito.verify(cloudContentRepository).write(eq(testFile4NameFile), any(), any(), eq(true), any())
	}

	@Test
	@DisplayName("move(\"/File 4x250\", \"/Directory 1/File 4x250\")")
	@Throws(BackendException::class)
	fun testMoveLongFileToNewLongFile() {
		val file4Name = "File " + Strings.repeat("4", 250)
		val file4Cipher = "file" + Strings.repeat("4", 250)
		val longFilenameBytes = file4Cipher.toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base32().encode(hash) + ".lng"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testFile4ContentFileOld = TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName", null, null)
		val cryptoFile4Old = CryptoFile(root, file4Name, "/$file4Name", null, testFile4ContentFileOld)
		val testFile4ContentFile = TestFile(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName", null, null)
		val testFile4NameFile = metadataFile(shortenedFileName)
		val cryptoMovedFile4 = CryptoFile(cryptoFolder1, file4Name, "/Directory 1/$file4Name", null, testFile4ContentFile)

		whenever(cloudContentRepository.move(testFile4ContentFileOld, testFile4ContentFile)).thenReturn(testFile4ContentFile)
		whenever(cloudContentRepository.create(testFile4NameFile.parent!!)).thenReturn(testFile4NameFile.parent)
		whenever(cloudContentRepository.write(eq(testFile4NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`(file4Cipher))
			testFile4NameFile
		}
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), file4Name, dirIdRoot.toByteArray())).thenReturn(file4Cipher)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), file4Name, dirId1.toByteArray())).thenReturn(file4Cipher)

		whenever(cloudContentRepository.file(bbFolder, shortenedFileName, null)).thenReturn(testFile4NameFile)

		// just for the exists check
		val longFilenameBytes0 = "0$file4Cipher".toByteArray(StandardCharsets.UTF_8)
		val hash0 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes0)
		val shortenedFileName0 = BaseEncoding.base32().encode(hash0) + ".lng"
		val testFile4NameFile0 = metadataFile(shortenedFileName0)
		whenever(cloudContentRepository.file(bbFolder, shortenedFileName0)).thenReturn(testFile4NameFile0)

		val targetFile: CloudFile = inTest.file(cryptoFolder1, file4Name) // needed due to ugly side effect
		val result = inTest.move(cryptoFile4Old, cryptoMovedFile4)

		Assertions.assertEquals(file4Name, result.name)
		Mockito.verify(cloudContentRepository).create(testFile4NameFile.parent!!)
		Mockito.verify(cloudContentRepository).write(eq(testFile4NameFile), any(), any(), eq(true), any())
		Mockito.verify(cloudContentRepository).move(testFile4ContentFileOld, testFile4ContentFile)
	}

	@Test
	@DisplayName("move(\"/Directory 1/File 4x250\", \"/File 4\")")
	@Throws(BackendException::class)
	fun testMoveLongFileToNewShortFile() {
		val file4Name = "File " + Strings.repeat("4", 250)
		val file4Cipher = "file" + Strings.repeat("4", 250)
		val longFilenameBytes = file4Cipher.toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base32().encode(hash) + ".lng"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testFile4 = TestFile(aaFolder, "file4", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4", null, null)
		val cryptoFile4 = CryptoFile(root, "File 4", "/File 4", null, testFile4)
		val testFile4DirFile = TestFile(bbFolder, "contents.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName", null, null)
		val testFile4NameFile = metadataFile(shortenedFileName)
		val cryptoMovedFile4 = CryptoFile(cryptoFolder1, file4Name, "/Directory 1/$file4Name", null, testFile4DirFile)

		whenever(cloudContentRepository.file(aaFolder, "file4.c9r")).thenReturn(testFile4)
		whenever(cloudContentRepository.move(testFile4DirFile, testFile4)).thenReturn(testFile4)
		whenever(cloudContentRepository.write(eq(testFile4NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("$file4Cipher.c9r"))
			testFile4NameFile
		}
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "File 4", dirIdRoot.toByteArray())).thenReturn("file4")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), file4Name, dirId1.toByteArray())).thenReturn(file4Cipher)

		// just for the exists check
		whenever(cloudContentRepository.file(bbFolder, shortenedFileName, null)).thenReturn(testFile4DirFile)
		whenever(cloudContentRepository.file(aaFolder, "0file4")).thenReturn(testFile4NameFile)

		val result = inTest.move(cryptoMovedFile4, cryptoFile4)
		Assertions.assertEquals(cryptoFile4, result)

		Mockito.verify(cloudContentRepository).move(testFile4DirFile, testFile4)
	}

	@Test
	@DisplayName("move(\"/Directory 1\", \"/Directory 15\")")
	@Throws(BackendException::class)
	fun testMoveShortFolderToNewShortFolder() {
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testDir15DirFile = TestFile(aaFolder, "0dir15", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/0dir15", null, null)
		val cryptoFolder15 = CryptoFolder(root, "Directory 15", "/Directory 15/", testDir15DirFile)

		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder15]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache.put(eq(cryptoFolder15), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(cloudContentRepository.file(aaFolder, "0dir15")).thenReturn(testDir15DirFile)
		whenever(cloudContentRepository.move(cryptoFolder1.dirFile!!, testDir15DirFile)).thenReturn(testDir15DirFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "Directory 15", dirIdRoot.toByteArray())).thenReturn("dir15")

		// just for the exists check
		whenever(cloudContentRepository.file(aaFolder, "dir15", null)).thenReturn(TestFile(rootFolder, "dir15", aaFolder.path + "/dir15", null, null))

		val result = inTest.move(cryptoFolder1, cryptoFolder15)

		Mockito.verify(cloudContentRepository).move(cryptoFolder1.dirFile!!, testDir15DirFile)
		Mockito.verify(dirIdCache).evict(cryptoFolder1)
		Mockito.verify(dirIdCache).evict(cryptoFolder15)
	}

	@Test
	@DisplayName("move(\"/Directory 1\", \"/Directory 15x200\")")
	@Throws(BackendException::class)
	fun testMoveShortFolderToNewLongFolder() {
		val dir15Name = "Dir " + Strings.repeat("15", 250)
		val dir15Cipher = "dir" + Strings.repeat("15", 250)
		val longFilenameBytes = "0$dir15Cipher".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base32().encode(hash) + ".lng"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testDir15DirFile = TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName", null, null)
		val testDir15NameFile = metadataFile(shortenedFileName)
		val cryptoFolder15 = CryptoFolder(root, dir15Name, "/$dir15Name", testDir15DirFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), dir15Name, dirId1.toByteArray())).thenReturn(dir15Cipher)
		whenever(cloudContentRepository.file(aaFolder, "dir15.c9r", null))
			.thenReturn(TestFile(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r", null, null))
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache.put(eq(cryptoFolder15), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder15]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(cloudContentRepository.move(cryptoFolder1.dirFile!!, testDir15DirFile)).thenReturn(testDir15DirFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), dir15Name, dirIdRoot.toByteArray())).thenReturn(dir15Cipher)
		whenever(cloudContentRepository.write(eq(testDir15NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("0$dir15Cipher"))
			testDir15NameFile
		}

		whenever(cloudContentRepository.file(aaFolder, shortenedFileName)).thenReturn(testDir15DirFile)

		// just for the exists check
		val longFilenameBytes0 = dir15Cipher.toByteArray(StandardCharsets.UTF_8)
		val hash0 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes0)
		val shortenedFileName0 = BaseEncoding.base32().encode(hash0) + ".lng"
		val testDir15DirFile0 = TestFile(aaFolder, shortenedFileName0, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName0", null, null)
		val testDir15NameFile0 = metadataFile(shortenedFileName0)
		whenever(cloudContentRepository.file(aaFolder, shortenedFileName0, null)).thenReturn(testDir15DirFile0)

		val targetFile = inTest.folder(root, dir15Name) // needed due to ugly side effect
		val result = inTest.move(cryptoFolder1, cryptoFolder15)

		Assertions.assertEquals(cryptoFolder15, result)
		Mockito.verify(cloudContentRepository).write(eq(testDir15NameFile), any(), any(), eq(true), any())
		Mockito.verify(cloudContentRepository).move(cryptoFolder1.dirFile!!, testDir15DirFile)
	}

	@Test
	@DisplayName("move(\"/Directory 15x200\", \"/Directory 3000\")")
	@Throws(BackendException::class)
	fun testMoveLongFolderToNewShortFolder() {
		val dir15Name = "Dir " + Strings.repeat("15", 250)
		val dir15Cipher = "dir" + Strings.repeat("15", 250)
		val longFilenameBytes = "0$dir15Cipher".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base32().encode(hash) + ".lng"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testDir15DirFile = TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName", null, null)
		val cryptoFolder15 = CryptoFolder(root, dir15Name, "/$dir15Name", testDir15DirFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), dir15Name, dirId1.toByteArray())).thenReturn(dir15Cipher)
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), dir15Name, dirIdRoot.toByteArray())).thenReturn(dir15Cipher)

		val testDir3DirFile = TestFile(aaFolder, "dir3", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3", null, null)
		val cryptoFolder3 = CryptoFolder(root, "Directory 3", "/Directory 3", testDir3DirFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base32(), "Directory 3", dirIdRoot.toByteArray())).thenReturn("dir3")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base32(), "dir3", dirIdRoot.toByteArray())).thenReturn("Directory 3")
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.create(lvl2Dir)).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.write(eq(testDir3DirFile), any(), any(), eq(false), any())).thenReturn(testDir3DirFile)
		whenever(cloudContentRepository.move(testDir15DirFile, testDir3DirFile)).thenReturn(testDir3DirFile)

		// just for the exists check
		whenever(cloudContentRepository.file(aaFolder, "0dir3")).thenReturn(TestFile(rootFolder, "0dir3", aaFolder.path + "/0dir3", null, null))
		whenever(cloudContentRepository.file(aaFolder, "dir3", null)).thenReturn(testDir3DirFile)

		val targetFile = inTest.folder(root, cryptoFolder3.name) // needed due to ugly side effect
		val result = inTest.move(cryptoFolder15, cryptoFolder3)

		Mockito.verify(cloudContentRepository).move(testDir15DirFile, cryptoFolder3.dirFile!!)
	}

	@Throws(BackendException::class)
	private fun metadataFile(shortFilename: String): CloudFile {
		whenever(cloudContentRepository.folder(rootFolder, "m")).thenReturn(m)
		val firstLevelFolder = TestFolder(m, shortFilename.substring(0, 2), "/m/" + shortFilename.substring(0, 2))
		whenever(cloudContentRepository.folder(m, shortFilename.substring(0, 2))).thenReturn(firstLevelFolder)
		val secondLevelFolder = TestFolder(firstLevelFolder, shortFilename.substring(2, 4), "/m/" + shortFilename.substring(0, 2) + "/" + shortFilename.substring(2, 4))
		whenever(cloudContentRepository.folder(firstLevelFolder, shortFilename.substring(2, 4))).thenReturn(secondLevelFolder)
		val file = TestFile(secondLevelFolder, shortFilename, "/m/" + shortFilename.substring(0, 2) + "/" + shortFilename.substring(2, 4) + "/" + shortFilename, null, null)
		whenever(cloudContentRepository.file(secondLevelFolder, shortFilename)).thenReturn(file)
		return file
	}
}
