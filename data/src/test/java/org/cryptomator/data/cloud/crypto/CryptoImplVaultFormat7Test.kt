package org.cryptomator.data.cloud.crypto

import android.content.Context
import com.google.common.base.Strings
import com.google.common.io.BaseEncoding
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
class CryptoImplVaultFormat7Test {

	private val dirIdRoot = ""
	private val dirId1 = "dir1-id"
	private val dirId2 = "dir2-id"

	private var cloud: Cloud = mock()
	private var cryptoCloud: CryptoCloud = mock()
	private var context: Context = mock()
	private var cryptor: Cryptor = mock()
	private var cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private var dirIdCache: DirIdCache = mock()
	private var fileNameCryptor: FileNameCryptor = mock()
	private var fileContentCryptor: FileContentCryptor = mock()
	private var fileHeaderCryptor: FileHeaderCryptor = mock()
	private var tmpDir = createTempDirectory()

	private lateinit var rootFolder: TestFolder
	private lateinit var d: TestFolder
	private lateinit var lvl2Dir: TestFolder
	private lateinit var aaFolder: TestFolder
	private lateinit var root: RootCryptoFolder
	private lateinit var cryptoFile1: CryptoFile
	private lateinit var cryptoFile2: CryptoFile
	private lateinit var cryptoFile4: CryptoFile
	private lateinit var cryptoFolder1: CryptoFolder

	private lateinit var inTest: CryptoImplVaultFormat7

	private fun <T> any(type: Class<T>): T = Mockito.any(type)

	@BeforeEach
	@Throws(BackendException::class)
	fun setup() {
		whenever(context.cacheDir).thenReturn(tmpDir.toFile())

		rootFolder = RootTestFolder(cloud)
		d = TestFolder(rootFolder, "d", "/d")
		lvl2Dir = TestFolder(d, "00", "/d/00")
		aaFolder = TestFolder(lvl2Dir, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")

		whenever(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor)
		whenever(cryptor.fileContentCryptor()).thenReturn(fileContentCryptor)
		whenever(cryptor.fileHeaderCryptor()).thenReturn(fileHeaderCryptor)

		root = RootCryptoFolder(cryptoCloud)
		inTest = CryptoImplVaultFormat7(context, { cryptor }, cloudContentRepository, rootFolder, dirIdCache)

		whenever(fileNameCryptor.hashDirectoryId(dirIdRoot)).thenReturn("00AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
		whenever(fileNameCryptor.hashDirectoryId(dirId1)).thenReturn("11BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		whenever(fileNameCryptor.hashDirectoryId(dirId2)).thenReturn("22CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir1", dirIdRoot.toByteArray())).thenReturn("Directory 1")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file1", dirIdRoot.toByteArray())).thenReturn("File 1")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file2", dirIdRoot.toByteArray())).thenReturn("File 2")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir2", dirId1.toByteArray())).thenReturn("Directory 2")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file3", dirId1.toByteArray())).thenReturn("File 3")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "file4", dirIdRoot.toByteArray())).thenReturn("File 4")

		val testFile1 = TestFile(aaFolder, "file1.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file1.c9r", null, null)
		val testFile2 = TestFile(aaFolder, "file2.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file2.c9r", null, null)
		val testFile4 = TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", null, null)
		val testDir1 = TestFolder(aaFolder, "dir1.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir1.c9r")
		val testDir1DirFile = TestFile(testDir1, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir1.c9r/dir.c9r", null, null)
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
		cryptoFolder1 = CryptoFolder(root, "Directory 1", "/Directory 1", testDir1DirFile)

		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "00")).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.folder(lvl2Dir, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).thenReturn(aaFolder)
		whenever(cloudContentRepository.file(testDir1, "dir.c9r")).thenReturn(testDir1DirFile)
		whenever(cloudContentRepository.exists(testDir1DirFile)).thenReturn(true)
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(dirId1.toByteArray()), out)
			null
		}.whenever(cloudContentRepository).read(eq(cryptoFolder1.dirFile!!), any(), any(), any())
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
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val ddLvl2Dir = TestFolder(d, "33", "/d/33")
		val ddFolder = TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		val testDir3 = TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName")
		val testDir3DirFile = TestFile(testDir3, "dir.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/dir.c9r", null, null)
		val testDir3NameFile = TestFile(testDir3, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/name.c9s", null, null)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir3Name, dirId1.toByteArray())).thenReturn(dir3Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), dir3Cipher, dirId1.toByteArray())).thenReturn(dir3Name)
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile)
		whenever(cloudContentRepository.file(testDir3, "name.c9s")).thenReturn(testDir3NameFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.toByteArray())).thenReturn("dir1")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 2", dirId1.toByteArray())).thenReturn("dir2")

		val cryptoFolder3 = CryptoFolder(cryptoFolder1, dir3Name, "/Directory 1/$dir3Name", testDir3DirFile)

		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream("dir3-id".toByteArray()), out)
			null
		}.whenever(cloudContentRepository).read(eq(cryptoFolder3.dirFile!!), any(), any(), any())

		/*
		 * │ ├─ Directory 3x250
		 * │ │ ├─ Directory 4x250
		 * │ │ └─ File 5x250
		 */
		val dir4Name = "Directory " + Strings.repeat("4", 250)
		val dir4Cipher = "dir" + Strings.repeat("4", 250)
		val longFilenameBytes4 = "$dir4Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash4 = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes4)
		val shortenedFileName4 = BaseEncoding.base64Url().encode(hash4) + ".c9s"
		val directory4x250 = TestFolder(ddFolder, shortenedFileName4, "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD$shortenedFileName4")
		val testDir4DirFile = TestFile(directory4x250, "dir.c9r", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/$shortenedFileName4/dir.c9r", null, null)
		val testDir4NameFile = TestFile(directory4x250, "name.c9s", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/$shortenedFileName4/name.c9s", null, null)

		whenever(cloudContentRepository.file(directory4x250, "dir.c9r")).thenReturn(testDir4DirFile)
		whenever(cloudContentRepository.file(directory4x250, "name.c9s")).thenReturn(testDir4NameFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir4Name, "dir3-id".toByteArray())).thenReturn(dir4Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), dir4Cipher, "dir3-id".toByteArray())).thenReturn(dir4Name)
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(dir4Cipher.toByteArray(charset("UTF-8"))), out)
			null
		}.whenever(cloudContentRepository).read(eq(testDir4NameFile), any(), any(), any())

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
		val shortenedFileName5 = BaseEncoding.base64Url().encode(hash5) + ".c9s"
		val directory5x250 = TestFolder(ddFolder, shortenedFileName5, "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD$shortenedFileName5")
		val testFile5ContentFile = TestFile(directory5x250, "contents.c9r", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/$shortenedFileName5/contents.c9r", null, null)
		val testFile5NameFile = TestFile(directory5x250, "name.c9s", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD/$shortenedFileName5/name.c9s", null, null)

		whenever(cloudContentRepository.file(directory5x250, "contents.c9r")).thenReturn(testFile5ContentFile)
		whenever(cloudContentRepository.file(directory5x250, "name.c9s")).thenReturn(testFile5NameFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file5Name, "dir3-id".toByteArray())).thenReturn(file5Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), file5Cipher, "dir3-id".toByteArray())).thenReturn(file5Name)
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(file5Cipher.toByteArray(charset("UTF-8"))), out)
			null
		}.whenever(cloudContentRepository).read(eq(testFile5NameFile), any(), any(), any())

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
		val header = Mockito.mock(FileHeader::class.java)

		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(8)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 1", dirIdRoot.toByteArray())).thenReturn("file1")
		whenever(fileHeaderCryptor.decryptHeader(StandardCharsets.UTF_8.encode("hhhhh"))).thenReturn(header)
		whenever(fileContentCryptor.decryptChunk(eq(StandardCharsets.UTF_8.encode("TOPSECRET!")), any(), eq(header), any()))
			.then { StandardCharsets.UTF_8.encode("geheim!!") }
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(file1Content), out)
			null
		}.whenever(cloudContentRepository).read(eq(cryptoFile1.cloudFile), any(), any(), any())

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
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val testFile3Folder = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testFile3ContentFile = TestFile(testFile3Folder, "content.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/content.c9r", null, null)
		val file1Content = "hhhhhTOPSECRET!TOPSECRET!TOPSECRET!TOPSECRET!".toByteArray()
		val header = Mockito.mock(FileHeader::class.java)

		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(8)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileHeaderCryptor.decryptHeader(StandardCharsets.UTF_8.encode("hhhhh"))).thenReturn(header)
		whenever(fileContentCryptor.decryptChunk(eq(StandardCharsets.UTF_8.encode("TOPSECRET!")), any(), eq(header), any()))
			.then { StandardCharsets.UTF_8.encode("geheim!!") }

		val cryptoFile15 = CryptoFile(root, file3Name, "/$file3Name", null, testFile3ContentFile)

		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(file1Content), out)
			null
		}.whenever(cloudContentRepository).read(eq(cryptoFile15.cloudFile), any(), any(), any())

		val outputStream = ByteArrayOutputStream(1000)
		inTest.read(cryptoFile15, outputStream, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)

		MatcherAssert.assertThat(outputStream.toString(), CoreMatchers.`is`("geheim!!geheim!!geheim!!geheim!!"))
	}

	@Test
	@DisplayName("write(\"/File 1\", text, NO_PROGRESS_AWARE, replace=false, 10bytes)")
	@Throws(BackendException::class)
	fun testWriteToShortFile() {
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 1", dirIdRoot.toByteArray())).thenReturn("file1")

		val header = Mockito.mock(FileHeader::class.java)

		whenever(fileHeaderCryptor.create()).thenReturn(header)
		whenever(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap("hhhhh".toByteArray()))
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(10)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(fileContentCryptor.encryptChunk(any(ByteBuffer::class.java), any(), any(FileHeader::class.java)))
			.thenAnswer { invocation: InvocationOnMock ->
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

		// just for the exits check
		val tmpFileExistFolder = TestFolder(aaFolder, cryptoFile1.cloudFile.name, cryptoFile1.cloudFile.path)
		whenever(cloudContentRepository.folder(aaFolder, cryptoFile1.cloudFile.name))
			.thenReturn(tmpFileExistFolder)
		whenever(cloudContentRepository.file(tmpFileExistFolder, "dir.c9r"))
			.thenReturn(TestFile(tmpFileExistFolder, "dir.c9r", tmpFileExistFolder.path + "/dir.c9r", null, null))

		val cryptoFile = inTest.write(cryptoFile1, from("TOPSECRET!".toByteArray(StandardCharsets.UTF_8)), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 10L)

		MatcherAssert.assertThat(cryptoFile, CoreMatchers.`is`(cryptoFile1))
	}

	@Test
	@DisplayName("write(\"/File 15x250\", text, NO_PROGRESS_AWARE, replace=false, 10bytes)")
	@Throws(BackendException::class)
	fun testWriteToLongFile() {
		val file15Name = "File " + Strings.repeat("15", 250)
		val file15Cipher = "file" + Strings.repeat("15", 250)
		val longFilenameBytes = "$file15Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val testFile3Folder = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testFile3WhatTheHellCLoudFile = TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName", null, null)
		val testFile15ContentFile = TestFile(testFile3Folder, "contents.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/contents.c9r", 10L, null)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file15Name, dirIdRoot.toByteArray())).thenReturn(file15Cipher)

		val cryptoFile15 = CryptoFile(root, file15Name, "/$file15Name", 15L, testFile3WhatTheHellCLoudFile)

		whenever(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testFile3Folder)
		whenever(cloudContentRepository.file(testFile3Folder, "contents.c9r", 10L)).thenReturn(testFile15ContentFile)
		whenever(cloudContentRepository.exists(testFile3Folder)).thenReturn(true)

		val header = Mockito.mock(FileHeader::class.java)

		whenever(fileHeaderCryptor.create()).thenReturn(header)
		whenever(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap("hhhhh".toByteArray()))
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(10)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(fileContentCryptor.encryptChunk(any(ByteBuffer::class.java), any(), any(FileHeader::class.java)))
			.thenAnswer { invocation: InvocationOnMock ->
				val input = invocation.getArgument<ByteBuffer>(0)
				val inStr = StandardCharsets.UTF_8.decode(input).toString()
				ByteBuffer.wrap(inStr.lowercase().toByteArray(StandardCharsets.UTF_8))
			}
		whenever(cloudContentRepository.write(eq(testFile15ContentFile), any(DataSource::class.java), any(), eq(false), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val encrypted = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
				MatcherAssert.assertThat(encrypted, CoreMatchers.`is`("hhhhhtopsecret!"))
				invocationOnMock.getArgument(0)
			}

		val cryptoFile = inTest.write(cryptoFile15, from("TOPSECRET!".toByteArray(StandardCharsets.UTF_8)), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 10L)

		MatcherAssert.assertThat(cryptoFile, CoreMatchers.`is`(cryptoFile15))

		Mockito.verify(cloudContentRepository).write(
			eq(testFile15ContentFile), any(
				DataSource::class.java
			), any(), eq(false), any()
		)
	}

	/*@Test FIXME
	@DisplayName("write(\"/File 15x250\", text, NO_PROGRESS_AWARE, replace=false, 10bytes)")
	@Throws(BackendException::class)
	fun testWriteToLongFileUsingAutoRename() {
		val file15Name = "File " + Strings.repeat("15", 250)
		val file15Cipher = "file" + Strings.repeat("15", 250)
		val longFilenameBytes = "$file15Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val testFile15Folder = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testFile15WhatTheHellCLoudFile = TestFile(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName", null, null)
		val testFile15ContentFile = TestFile(testFile15Folder, "contents.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/contents.c9r", 10L, null)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file15Name, dirIdRoot.toByteArray())).thenReturn(file15Cipher)

		val file15CipherRename = "$file15Cipher(1)"
		val hashRename = MessageDigestSupplier.SHA1.get().digest("$file15CipherRename.c9r".toByteArray(StandardCharsets.UTF_8))
		val shortenedFileNameRename = BaseEncoding.base64Url().encode(hashRename) + ".c9s"
		val testFile15FolderRename = TestFolder(aaFolder, shortenedFileNameRename, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileNameRename")
		val testFile15WhatTheHellCloudFileRename = TestFile(aaFolder, shortenedFileNameRename, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileNameRename", 20L, null)
		val testFile15ContentFileRename = TestFile(testFile15FolderRename, "contents.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileNameRename/contents.c9r", 10L, null)
		val testFile15NameFileRename = TestFile(testFile15FolderRename, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileNameRename/name.c9s", 511L, null)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "$file15Name (1)", dirIdRoot.toByteArray())).thenReturn("$file15Cipher(1)")

		val cryptoFile15 = CryptoFile(root, file15Name, "/$file15Name", 15L, testFile15WhatTheHellCLoudFile)

		whenever(cloudContentRepository.file(testFile15Folder, "contents.c9r", 10L)).thenReturn(testFile15ContentFile)
		whenever(cloudContentRepository.exists(testFile15ContentFile)).thenReturn(true)
		whenever(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testFile15Folder)
		whenever(cloudContentRepository.exists(testFile15Folder)).thenReturn(true)
		whenever(cloudContentRepository.file(testFile15Folder, "contents.c9r", 10L)).thenReturn(testFile15ContentFile)
		whenever(cloudContentRepository.file(testFile15FolderRename, "contents.c9r", 15L)).thenReturn(testFile15ContentFileRename)
		whenever(cloudContentRepository.folder(aaFolder, shortenedFileNameRename)).thenReturn(testFile15FolderRename)
		whenever(cloudContentRepository.exists(testFile15FolderRename)).thenReturn(false)
		whenever(cloudContentRepository.create(testFile15FolderRename)).thenReturn(testFile15FolderRename)
		whenever(cloudContentRepository.file(testFile15FolderRename, "name.c9s", 511L)).thenReturn(testFile15NameFileRename)
		whenever(cloudContentRepository.write(eq(testFile15NameFileRename), any(DataSource::class.java), any(), eq(true), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val encrypted = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
				MatcherAssert.assertThat(encrypted, CoreMatchers.`is`("$file15CipherRename.c9r"))
				invocationOnMock.getArgument(0)
			}
		whenever(cloudContentRepository.file(aaFolder, shortenedFileNameRename, 20L)).thenReturn(testFile15WhatTheHellCloudFileRename)

		val header = Mockito.mock(FileHeader::class.java)

		whenever(fileHeaderCryptor.create()).thenReturn(header)
		whenever(fileHeaderCryptor.encryptHeader(header)).thenReturn(ByteBuffer.wrap("hhhhh".toByteArray()))
		whenever(fileHeaderCryptor.headerSize()).thenReturn(5)
		whenever(fileContentCryptor.cleartextChunkSize()).thenReturn(10)
		whenever(fileContentCryptor.ciphertextChunkSize()).thenReturn(10)
		whenever(fileContentCryptor.encryptChunk(any(ByteBuffer::class.java), any(), any(FileHeader::class.java)))
			.thenAnswer { invocation: InvocationOnMock ->
				val input = invocation.getArgument<ByteBuffer>(0)
				val inStr = StandardCharsets.UTF_8.decode(input).toString()
				ByteBuffer.wrap(inStr.lowercase().toByteArray(StandardCharsets.UTF_8))
			}
		whenever(cloudContentRepository.write(eq(testFile15ContentFileRename), any(DataSource::class.java), any(), eq(false), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val encrypted = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
				MatcherAssert.assertThat(encrypted, CoreMatchers.`is`("hhhhhtopsecret!"))
				invocationOnMock.getArgument(0)
			}

		val cryptoFile = inTest.write(cryptoFile15, from("TOPSECRET!".toByteArray(StandardCharsets.UTF_8)), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 10L)

		MatcherAssert.assertThat(cryptoFile, CoreMatchers.`is`(cryptoFile15))
		Mockito.verify(cloudContentRepository).create(testFile15FolderRename)
		Mockito.verify(cloudContentRepository).write(
			eq(testFile15NameFileRename), any(
				DataSource::class.java
			), any(), eq(true), any()
		)
		Mockito.verify(cloudContentRepository).write(
			eq(testFile15ContentFileRename), any(
				DataSource::class.java
			), any(), eq(false), any()
		)
	}*/

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
		val testDir3 = TestFolder(aaFolder, "dir3.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r")
		val testDir3DirFile = TestFile(testDir3, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r/dir.c9r", null, null)
		val cryptoFolder3 = CryptoFolder(root, "Directory 3", "/Directory 3", testDir3DirFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 3", dirIdRoot.toByteArray())).thenReturn("dir3")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir3", dirIdRoot.toByteArray())).thenReturn("Directory 3")
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile)
		whenever(dirIdCache.put(eq(cryptoFolder3), any())).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(cloudContentRepository.create(lvl2Dir)).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.create(ddFolder)).thenReturn(ddFolder)
		whenever(cloudContentRepository.create(testDir3)).thenReturn(testDir3)
		whenever(cloudContentRepository.write(eq(testDir3DirFile), any(), any(), eq(false), any())).thenReturn(testDir3DirFile)

		// just for the exits check
		whenever(cloudContentRepository.file(aaFolder, "dir3.c9r", null))
			.thenReturn(TestFile(aaFolder, "dir3.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r", null, null))

		val cloudFolder: CloudFolder = inTest.create(cryptoFolder3)

		MatcherAssert.assertThat(cloudFolder, CoreMatchers.`is`(cryptoFolder3))

		Mockito.verify(cloudContentRepository).create(ddFolder)
		Mockito.verify(cloudContentRepository).create(testDir3)
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
		val longFilenameBytes = "$dir3Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val ddLvl2Dir = TestFolder(d, "33", "/d/33")
		val ddFolder = TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		val testDir3 = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testDir3DirFile = TestFile(testDir3, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/dir.c9r", null, null)
		val testDir3NameFile = TestFile(testDir3, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/name.c9s", 257L, null)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir3Name, dirIdRoot.toByteArray())).thenReturn(dir3Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), dir3Cipher, dirIdRoot.toByteArray())).thenReturn(dir3Name)
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir3)
		whenever(cloudContentRepository.exists(testDir3)).thenReturn(false)
		whenever(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile)
		whenever(cloudContentRepository.file(testDir3, "name.c9s", 257L)).thenReturn(testDir3NameFile)

		val cryptoFolder3 = CryptoFolder(root, dir3Name, "/$dir3Name", testDir3DirFile)

		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile)
		whenever(dirIdCache.put(eq(cryptoFolder3), any())).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(dirIdCache[cryptoFolder3]).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(cloudContentRepository.create(ddLvl2Dir)).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.create(ddFolder)).thenReturn(ddFolder)
		whenever(cloudContentRepository.create(testDir3)).thenReturn(testDir3)
		whenever(cloudContentRepository.write(eq(testDir3DirFile), any(), any(), eq(false), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("dir3-id"))
			testDir3DirFile
		}
		whenever(cloudContentRepository.write(eq(testDir3NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val nameContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(nameContent, CoreMatchers.`is`("$dir3Cipher.c9r"))
			testDir3NameFile
		}
		// just for the exits check
		whenever(cloudContentRepository.file(aaFolder, "dir3.c9r", null))
			.thenReturn(TestFile(aaFolder, "dir3.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r", null, null))

		var cloudFolder: CloudFolder = inTest.folder(root, dir3Name)

		cloudFolder = inTest.create(cryptoFolder3)

		MatcherAssert.assertThat(cloudFolder, CoreMatchers.`is`(cryptoFolder3))
		Mockito.verify(cloudContentRepository).create(ddFolder)
		Mockito.verify(cloudContentRepository).create(testDir3)
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
		val longFilenameBytes = "$file15Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val testFile3Folder = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testFile3ContentFile = TestFile(testFile3Folder, "content.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/content.c9r", null, null)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file15Name, dirIdRoot.toByteArray())).thenReturn(file15Cipher)

		val cryptoFile15 = CryptoFile(root, file15Name, "/$file15Name", 15L, testFile3ContentFile)

		whenever(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testFile3Folder)

		inTest.delete(cryptoFile15)

		Mockito.verify(cloudContentRepository).delete(testFile3Folder)
	}

	@Test
	@DisplayName("delete(\"/Directory 1/Directory 2/\")")
	@Throws(BackendException::class)
	fun testDeleteSingleShortFolder() {
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val ccLvl2Dir = TestFolder(d, "22", "/d/22")
		val ccFolder = TestFolder(ccLvl2Dir, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC", "/d/22/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 1", dirIdRoot.toByteArray())).thenReturn("dir1")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 2", dirId1.toByteArray())).thenReturn("dir2")

		val testDir2 = TestFolder(bbFolder, "dir2.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/dir2.c9r")
		val testDir2DirFile = TestFile(testDir2, "dir.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/dir2.c9r/dir.c9r", null, null)
		val cryptoFolder2 = CryptoFolder(cryptoFolder1, "Directory 2", "/Directory 1/Directory 2", testDir2DirFile)

		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(2)
			copyStreamToStream(ByteArrayInputStream(dirId2.toByteArray()), out)
			null
		}.whenever(cloudContentRepository).read(eq(cryptoFolder2.dirFile!!), any(), any(), any())

		val dir1Items: ArrayList<CloudNode> = object : ArrayList<CloudNode>() {
			init {
				add(testDir2)
			}
		}

		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "22")).thenReturn(ccLvl2Dir)
		whenever(cloudContentRepository.folder(ccLvl2Dir, "CCCCCCCCCCCCCCCCCCCCCCCCCCCCCC")).thenReturn(ccFolder)
		whenever(cloudContentRepository.file(testDir2, "dir.c9r")).thenReturn(testDir2DirFile)
		whenever<List<*>>(cloudContentRepository.list(bbFolder)).thenReturn(dir1Items)
		whenever(dirIdCache.put(eq(cryptoFolder2), any())).thenReturn(DirIdInfo(dirId2, ccFolder))
		whenever(dirIdCache[cryptoFolder2]).thenReturn(DirIdInfo(dirId2, ccFolder))
		whenever(cloudContentRepository.exists(testDir2DirFile)).thenReturn(true)
		whenever<List<*>>(cloudContentRepository.list(ccFolder)).thenReturn(ArrayList<CloudNode>())

		inTest.delete(cryptoFolder2)

		Mockito.verify(cloudContentRepository).delete(ccFolder)
		Mockito.verify(cloudContentRepository).delete(testDir2)
		Mockito.verify(dirIdCache).evict(cryptoFolder2)
	}

	@Test
	@DisplayName("delete(\"/Directory 3x250\")")
	@Throws(BackendException::class)
	fun testDeleteSingleLongFolder() {
		val dir3Name = "Directory " + Strings.repeat("3", 250)
		val dir3Cipher = "dir" + Strings.repeat("3", 250)
		val longFilenameBytes = "$dir3Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val ddLvl2Dir = TestFolder(d, "33", "/d/33")
		val ddFolder = TestFolder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		val testDir3 = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testDir3DirFile = TestFile(testDir3, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/dir.c9r", null, null)
		val testDir3NameFile = TestFile(testDir3, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/name.c9s", 257L, null)
		val cryptoFolder3 = CryptoFolder(root, dir3Name, "/$dir3Name", testDir3DirFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir3Name, dirIdRoot.toByteArray())).thenReturn(dir3Cipher)
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), dir3Cipher, dirIdRoot.toByteArray())).thenReturn(dir3Name)
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(ddLvl2Dir)
		whenever(cloudContentRepository.folder(ddLvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir3)
		whenever(cloudContentRepository.exists(testDir3)).thenReturn(false)
		whenever(dirIdCache.put(eq(cryptoFolder3), any())).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile)
		whenever(cloudContentRepository.file(testDir3, "name.c9s", 257L)).thenReturn(testDir3NameFile)
		whenever<List<*>>(cloudContentRepository.list(ddFolder)).thenReturn(ArrayList<CloudNode>())
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")

		inTest.delete(cryptoFolder3)

		Mockito.verify(cloudContentRepository).delete(ddFolder)
		Mockito.verify(cloudContentRepository).delete(testDir3)
		Mockito.verify(dirIdCache).evict(cryptoFolder3)
	}

	@Test
	@DisplayName("move(\"/File 4\", \"/Directory 1/File 4\")")
	@Throws(BackendException::class)
	fun testMoveShortFileToNewShortFile() {
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testFile4 = TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", null, null)
		val testMovedFile4 = TestFile(bbFolder, "file4.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/file4.c9r", null, null)
		val cryptoFile4 = CryptoFile(root, "File 4", "/File 4", null, testFile4)
		val cryptoMovedFile4 = CryptoFile(cryptoFolder1, "File 4", "/Directory 1/File 4", null, testMovedFile4)

		whenever(cloudContentRepository.file(aaFolder, "file4.c9r")).thenReturn(testFile4)
		whenever(cloudContentRepository.file(bbFolder, "file4.c9r")).thenReturn(testMovedFile4)
		whenever(cloudContentRepository.move(testFile4, testMovedFile4)).thenReturn(testMovedFile4)
		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(cloudContentRepository.folder(bbFolder, "file4.c9r")).thenReturn(null)
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirId1.toByteArray())).thenReturn("file4")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.toByteArray())).thenReturn("file4")

		// just for the exits check
		val tmpFileExistFolder = TestFolder(bbFolder, testMovedFile4.name, testMovedFile4.path)
		whenever(cloudContentRepository.folder(bbFolder, testMovedFile4.name))
			.thenReturn(tmpFileExistFolder)
		whenever(cloudContentRepository.file(tmpFileExistFolder, "dir.c9r"))
			.thenReturn(TestFile(tmpFileExistFolder, "dir.c9r", tmpFileExistFolder.path + "/dir.c9r", null, null))

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
		val longFilenameBytes = "$file4Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testFile4 = TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", null, null)
		val cryptoFile4 = CryptoFile(root, "File 4", "/File 4", null, testFile4)
		val testDir4 = TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName")
		val testFile4ContentFile = TestFile(testDir4, "contents.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/contents.c9r", null, null)
		val testFile4NameFile = TestFile(testDir4, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/name.c9s", 258L, null)
		val testFile4WhatTheHellCLoudFile = TestFile(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName", null, null) // ugly hack
		val cryptoMovedFile4 = CryptoFile(cryptoFolder1, file4Name, "/Directory 1/$file4Name", null, testFile4WhatTheHellCLoudFile)

		whenever(cloudContentRepository.file(aaFolder, "file4.c9r")).thenReturn(testFile4)
		whenever(cloudContentRepository.file(testDir4, "contents.c9r")).thenReturn(testFile4ContentFile)
		whenever(cloudContentRepository.file(testDir4, "name.c9s")).thenReturn(testFile4NameFile)
		whenever(cloudContentRepository.file(testDir4, "name.c9s", 258L)).thenReturn(testFile4NameFile)
		whenever(cloudContentRepository.file(bbFolder, shortenedFileName, null)).thenReturn(testFile4WhatTheHellCLoudFile)
		whenever(cloudContentRepository.move(testFile4, testFile4ContentFile)).thenReturn(testFile4ContentFile)
		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(cloudContentRepository.folder(bbFolder, "file4.c9r")).thenReturn(null)
		whenever(cloudContentRepository.folder(bbFolder, shortenedFileName)).thenReturn(testDir4)
		whenever(cloudContentRepository.create(testDir4)).thenReturn(testDir4)
		whenever(cloudContentRepository.write(eq(testFile4NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("$file4Cipher.c9r"))
			testFile4NameFile
		}
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.toByteArray())).thenReturn("file4")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file4Name, dirId1.toByteArray())).thenReturn(file4Cipher)

		val targetFile = inTest.file(cryptoFolder1, file4Name) // needed due to ugly side effect
		val result = inTest.move(cryptoFile4, cryptoMovedFile4)

		Assertions.assertEquals(file4Name, result.name)

		Mockito.verify(cloudContentRepository).create(testDir4)
		Mockito.verify(cloudContentRepository).move(testFile4, testFile4ContentFile)
		Mockito.verify(cloudContentRepository).write(eq(testFile4NameFile), any(), any(), eq(true), any())
	}

	@Test
	@DisplayName("move(\"/File 4x250\", \"/Directory 1/File 4x250\")")
	@Throws(BackendException::class)
	fun testMoveLongFileToNewLongFile() {
		val file4Name = "File " + Strings.repeat("4", 250)
		val file4Cipher = "file" + Strings.repeat("4", 250)
		val longFilenameBytes = "$file4Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testDir4Old = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testFile4ContentFileOld = TestFile(testDir4Old, "contents.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/contents.c9r", null, null)
		val cryptoFile4Old = CryptoFile(root, file4Name, "/$file4Name", null, testFile4ContentFileOld)
		val testDir4 = TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName")
		val testFile4ContentFile = TestFile(testDir4, "contents.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/contents.c9r", null, null)
		val testFile4NameFile = TestFile(testDir4, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/name.c9s", 258L, null)
		val testFile4WhatTheHellCLoudFile = TestFile(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName", null, null) // ugly hack
		val cryptoMovedFile4 = CryptoFile(cryptoFolder1, file4Name, "/Directory 1/$file4Name", null, testFile4WhatTheHellCLoudFile)

		whenever(cloudContentRepository.file(testDir4, "contents.c9r")).thenReturn(testFile4ContentFile)
		whenever(cloudContentRepository.file(testDir4, "name.c9s")).thenReturn(testFile4NameFile)
		whenever(cloudContentRepository.file(testDir4, "name.c9s", 258L)).thenReturn(testFile4NameFile)
		whenever(cloudContentRepository.file(bbFolder, shortenedFileName, null)).thenReturn(testFile4WhatTheHellCLoudFile)
		whenever(cloudContentRepository.file(testDir4Old, "contents.c9r")).thenReturn(testFile4ContentFileOld)
		whenever(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir4Old)
		whenever(cloudContentRepository.move(testFile4ContentFileOld, testFile4ContentFile)).thenReturn(testFile4ContentFile)
		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(cloudContentRepository.folder(bbFolder, "file4.c9r")).thenReturn(null)
		whenever(cloudContentRepository.folder(bbFolder, shortenedFileName)).thenReturn(testDir4)
		whenever(cloudContentRepository.create(testDir4)).thenReturn(testDir4)
		whenever(cloudContentRepository.write(eq(testFile4NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("$file4Cipher.c9r"))
			testFile4NameFile
		}
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file4Name, dirIdRoot.toByteArray())).thenReturn(file4Cipher)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file4Name, dirId1.toByteArray())).thenReturn(file4Cipher)

		val targetFile: CloudFile = inTest.file(cryptoFolder1, file4Name) // needed due to ugly side effect
		val result = inTest.move(cryptoFile4Old, cryptoMovedFile4)

		Assertions.assertEquals(file4Name, result.name)
		Mockito.verify(cloudContentRepository).create(testDir4)
		Mockito.verify(cloudContentRepository).write(eq(testFile4NameFile), any(), any(), eq(true), any())
		Mockito.verify(cloudContentRepository).move(testFile4ContentFileOld, testFile4ContentFile)
		Mockito.verify(cloudContentRepository).delete(testDir4Old)
	}

	@Test
	@DisplayName("move(\"/Directory 1/File 4x250\", \"/File 4\")")
	@Throws(BackendException::class)
	fun testMoveLongFileToNewShortFile() {
		val file4Name = "File " + Strings.repeat("4", 250)
		val file4Cipher = "file" + Strings.repeat("4", 250)
		val longFilenameBytes = "$file4Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testFile4 = TestFile(aaFolder, "file4.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/file4.c9r", null, null)
		val cryptoFile4 = CryptoFile(root, "File 4", "/File 4", null, testFile4)
		val testDir4 = TestFolder(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName")
		val testFile4ContentFile = TestFile(testDir4, "contents.c9r", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/contents.c9r", null, null)
		val testFile4NameFile = TestFile(testDir4, "name.c9s", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName/name.c9s", 258L, null)
		val testFile4WhatTheHellCLoudFile = TestFile(bbFolder, shortenedFileName, "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB/$shortenedFileName", null, null) // ugly hack
		val cryptoMovedFile4 = CryptoFile(cryptoFolder1, file4Name, "/Directory 1/$file4Name", null, testFile4ContentFile)

		whenever(cloudContentRepository.file(aaFolder, "file4.c9r")).thenReturn(testFile4)
		whenever(cloudContentRepository.file(testDir4, "contents.c9r")).thenReturn(testFile4ContentFile)
		whenever(cloudContentRepository.file(testDir4, "name.c9s")).thenReturn(testFile4NameFile)
		whenever(cloudContentRepository.file(testDir4, "name.c9s", 258L)).thenReturn(testFile4NameFile)
		whenever(cloudContentRepository.file(bbFolder, shortenedFileName, null)).thenReturn(testFile4WhatTheHellCLoudFile) // bad
		whenever(cloudContentRepository.move(testFile4ContentFile, testFile4)).thenReturn(testFile4)
		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(cloudContentRepository.folder(bbFolder, "file4.c9r")).thenReturn(null)
		whenever(cloudContentRepository.folder(bbFolder, shortenedFileName)).thenReturn(testDir4)
		whenever(cloudContentRepository.create(testDir4)).thenReturn(testDir4)
		whenever(cloudContentRepository.write(eq(testFile4NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("$file4Cipher.c9r"))
			testFile4NameFile
		}
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "File 4", dirIdRoot.toByteArray())).thenReturn("file4")
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), file4Name, dirId1.toByteArray())).thenReturn(file4Cipher)

		// just for the exits check
		val tmpFileExistFolder = TestFolder(aaFolder, testFile4.name, testFile4.path)
		whenever(cloudContentRepository.folder(aaFolder, testFile4.name))
			.thenReturn(tmpFileExistFolder)
		whenever(cloudContentRepository.file(tmpFileExistFolder, "dir.c9r"))
			.thenReturn(TestFile(tmpFileExistFolder, "dir.c9r", tmpFileExistFolder.path + "/dir.c9r", null, null))

		val result = inTest.move(cryptoMovedFile4, cryptoFile4)

		Mockito.verify(cloudContentRepository).delete(testDir4)
		Mockito.verify(cloudContentRepository).move(testFile4ContentFile, testFile4)
	}

	@Test
	@DisplayName("move(\"/Directory 1\", \"/Directory 15\")")
	@Throws(BackendException::class)
	fun testMoveShortFolderToNewShortFolder() {
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testDir15 = TestFolder(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r")
		val testDir15DirFile = TestFile(testDir15, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r/dir.c9r", null, null)
		val cryptoFolder15 = CryptoFolder(root, "Directory 15", "/Directory 15/", testDir15DirFile)

		whenever(cloudContentRepository.file(aaFolder, "dir15.c9r", null))
			.thenReturn(TestFile(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r", null, null))
		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache.put(eq(cryptoFolder15), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder1]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache[cryptoFolder15]).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(cloudContentRepository.create(testDir15)).thenReturn(testDir15)
		whenever(cloudContentRepository.file(testDir15, "dir.c9r")).thenReturn(testDir15DirFile)
		whenever(cloudContentRepository.move(cryptoFolder1.dirFile!!, testDir15DirFile)).thenReturn(testDir15DirFile)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 15", dirIdRoot.toByteArray())).thenReturn("dir15")

		val result = inTest.move(cryptoFolder1, cryptoFolder15)

		Mockito.verify(cloudContentRepository).create(testDir15)
		Mockito.verify(cloudContentRepository).move(cryptoFolder1.dirFile!!, testDir15DirFile)
		Mockito.verify(cloudContentRepository).delete(cryptoFolder1.dirFile!!.parent!!)
	}

	@Test
	@DisplayName("move(\"/Directory 1\", \"/Directory 15x200\")")
	@Throws(BackendException::class)
	fun testMoveShortFolderToNewLongFolder() {
		val dir15Name = "Dir " + Strings.repeat("15", 250)
		val dir15Cipher = "dir" + Strings.repeat("15", 250)
		val longFilenameBytes = "$dir15Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testDir15 = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testDir15DirFile = TestFile(testDir15, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/dir.c9r", null, null)
		val testDir15NameFile = TestFile(testDir15, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/name.c9s", 507L, null)
		val cryptoFolder15 = CryptoFolder(root, dir15Name, "/$dir15Name", testDir15DirFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir15Name, dirId1.toByteArray())).thenReturn(dir15Cipher)
		whenever(cloudContentRepository.file(aaFolder, "dir15.c9r", null))
			.thenReturn(TestFile(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r", null, null))
		whenever(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir15)
		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache.put(eq(cryptoFolder15), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(cloudContentRepository.create(testDir15)).thenReturn(testDir15)
		whenever(cloudContentRepository.file(testDir15, "dir.c9r")).thenReturn(testDir15DirFile)
		whenever(cloudContentRepository.file(testDir15, "name.c9s", 507L)).thenReturn(testDir15NameFile)
		whenever(cloudContentRepository.move(cryptoFolder1.dirFile!!, testDir15DirFile)).thenReturn(testDir15DirFile)
		whenever(cloudContentRepository.create(testDir15)).thenReturn(testDir15)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir15Name, dirIdRoot.toByteArray())).thenReturn(dir15Cipher)
		whenever(cloudContentRepository.write(eq(testDir15NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("$dir15Cipher.c9r"))
			testDir15NameFile
		}

		val targetFile = inTest.folder(root, dir15Name) // needed due to ugly side effect
		val result = inTest.move(cryptoFolder1, cryptoFolder15)

		Mockito.verify(cloudContentRepository).create(testDir15)
		Mockito.verify(cloudContentRepository).move(cryptoFolder1.dirFile!!, testDir15DirFile)
		Mockito.verify(cloudContentRepository).write(eq(testDir15NameFile), any(), any(), eq(true), any())
		Mockito.verify(cloudContentRepository).delete(cryptoFolder1.dirFile!!.parent!!)
	}

	@Test
	@DisplayName("move(\"/Directory 15x200\", \"/Directory 3000\")")
	@Throws(BackendException::class)
	fun testMoveLongFolderToNewShortFolder() {
		val dir15Name = "Dir " + Strings.repeat("15", 250)
		val dir15Cipher = "dir" + Strings.repeat("15", 250)
		val longFilenameBytes = "$dir15Cipher.c9r".toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortenedFileName = BaseEncoding.base64Url().encode(hash) + ".c9s"
		val bbLvl2Dir = TestFolder(d, "11", "/d/11")
		val bbFolder = TestFolder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB", "/d/11/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
		val testDir15 = TestFolder(aaFolder, shortenedFileName, "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName")
		val testDir15DirFile = TestFile(testDir15, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/dir.c9r", null, null)
		val testDir15NameFile = TestFile(testDir15, "name.c9s", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/$shortenedFileName/name.c9s", 507L, null)
		val cryptoFolder15 = CryptoFolder(root, dir15Name, "/$dir15Name", testDir15DirFile)

		lvl2Dir = TestFolder(d, "33", "/d/33")

		val ddFolder = TestFolder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD", "/d/33/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		val testDir3 = TestFolder(aaFolder, "dir3.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r")
		val testDir3DirFile = TestFile(testDir3, "dir.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r/dir.c9r", null, null)
		val cryptoFolder3 = CryptoFolder(root, "Directory 3", "/Directory 3", testDir3DirFile)

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir15Name, dirId1.toByteArray())).thenReturn(dir15Cipher)
		whenever(cloudContentRepository.file(aaFolder, "dir15.c9r", null))
			.thenReturn(TestFile(aaFolder, "dir15.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir15.c9r", null, null))
		whenever(cloudContentRepository.folder(aaFolder, shortenedFileName)).thenReturn(testDir15)
		whenever(cloudContentRepository.folder(rootFolder, "d")).thenReturn(d)
		whenever(cloudContentRepository.folder(d, "11")).thenReturn(bbLvl2Dir)
		whenever(cloudContentRepository.folder(bbLvl2Dir, "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")).thenReturn(bbFolder)
		whenever(dirIdCache.put(eq(cryptoFolder1), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(dirIdCache.put(eq(cryptoFolder15), any())).thenReturn(DirIdInfo(dirId1, bbFolder))
		whenever(cloudContentRepository.create(testDir15)).thenReturn(testDir15)
		whenever(cloudContentRepository.file(testDir15, "dir.c9r")).thenReturn(testDir15DirFile)
		whenever(cloudContentRepository.file(testDir15, "name.c9s", 507L)).thenReturn(testDir15NameFile)
		whenever(cloudContentRepository.move(testDir15DirFile, cryptoFolder3.dirFile!!)).thenReturn(cryptoFolder3.dirFile)
		whenever(cloudContentRepository.create(testDir15)).thenReturn(testDir15)
		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), dir15Name, dirIdRoot.toByteArray())).thenReturn(dir15Cipher)
		whenever(cloudContentRepository.write(eq(testDir15NameFile), any(), any(), eq(true), any())).thenAnswer { invocationOnMock: InvocationOnMock ->
			val inputStream = invocationOnMock.getArgument<DataSource>(1)
			val dirContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).readLine()
			MatcherAssert.assertThat(dirContent, CoreMatchers.`is`("$dir15Cipher.c9r"))
			testDir15NameFile
		}

		whenever(fileNameCryptor.encryptFilename(BaseEncoding.base64Url(), "Directory 3", dirIdRoot.toByteArray())).thenReturn("dir3")
		whenever(fileNameCryptor.decryptFilename(BaseEncoding.base64Url(), "dir3", dirIdRoot.toByteArray())).thenReturn("Directory 3")
		whenever(fileNameCryptor.hashDirectoryId(AdditionalMatchers.not(eq("")))).thenReturn("33DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")
		whenever(cloudContentRepository.folder(d, "33")).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.folder(aaFolder, "dir3.c9r")).thenReturn(testDir3)
		whenever(cloudContentRepository.folder(lvl2Dir, "DDDDDDDDDDDDDDDDDDDDDDDDDDDDDD")).thenReturn(ddFolder)
		whenever(cloudContentRepository.file(testDir3, "dir.c9r")).thenReturn(testDir3DirFile)
		whenever(dirIdCache.put(eq(cryptoFolder3), any())).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(dirIdCache[cryptoFolder3]).thenReturn(DirIdInfo("dir3-id", ddFolder))
		whenever(cloudContentRepository.create(lvl2Dir)).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.create(ddFolder)).thenReturn(ddFolder)
		whenever(cloudContentRepository.create(testDir3)).thenReturn(testDir3)
		whenever(cloudContentRepository.write(eq(testDir3DirFile), any(), any(), eq(false), any())).thenReturn(testDir3DirFile)
		// just for the exits check
		whenever(cloudContentRepository.file(aaFolder, "dir3.c9r", null))
			.thenReturn(TestFile(aaFolder, "dir3.c9r", "/d/00/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/dir3.c9r", null, null))

		val targetFile = inTest.folder(root, cryptoFolder3.name) // needed due to ugly side effect
		val result = inTest.move(cryptoFolder15, cryptoFolder3)

		Mockito.verify(cloudContentRepository).create(testDir3)
		Mockito.verify(cloudContentRepository).move(testDir15DirFile, cryptoFolder3.dirFile!!)
		Mockito.verify(cloudContentRepository).delete(testDir15)
	}
}
