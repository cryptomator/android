package org.cryptomator.data.cloud.smb

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.session.SMB2GuestSigningRequiredException
import com.hierynomus.smbj.share.DiskEntry
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import org.cryptomator.data.cloud.smb.SmbConnectionPool.Retry
import org.cryptomator.data.util.LocalNetworkAccessDeniedException
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.UnableToDecryptSmbPasswordException
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.util.Optional
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.FatalCryptoException
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.security.GeneralSecurityException
import java.util.Date
import java.util.concurrent.TimeoutException

class SmbImplTest {

	private val cloud = SmbCloud.aSmbCloud() //
		.withId(1L) //
		.withUrl("smb://nas/share/base") //
		.withUsername("alice") //
		.withPassword("encryptedPassword") //
		.build()
	private val connectionPool = mock<SmbConnectionPool>()
	private val share = mock<DiskShare>()
	private val inTest = SmbImpl(cloud, connectionPool, mock<Context>())

	@BeforeEach
	fun setup() {
		whenever(connectionPool.withShare<Any?>(any(), any(), any())).thenAnswer {
			it.getArgument<(DiskShare) -> Any?>(2).invoke(share)
		}
	}

	@ParameterizedTest
	@MethodSource("errors")
	fun testErrorIsMappedToDomainException(error: Exception, expected: Class<out Exception>) {
		val folder = inTest.resolve("/vault")
		whenever(share.folderExists("base\\vault")).thenThrow(error)

		val thrown = assertThrows(Exception::class.java) { inTest.exists(folder) }

		assertThat(thrown, instanceOf(expected))
	}

	@Test
	fun testAccessDeniedOnFileIsNoCredentialProblem() {
		val folder = inTest.resolve("/vault")
		whenever(share.folderExists("base\\vault")).thenThrow(SMBApiException(NtStatus.STATUS_ACCESS_DENIED.value, SMB2MessageCommandCode.SMB2_CREATE, null))

		assertThrows(FatalBackendException::class.java) { inTest.exists(folder) }
	}

	@Test
	fun testUndecryptablePasswordIsReported() {
		whenever(connectionPool.withShare<Any?>(any(), any(), any())).thenAnswer {
			it.getArgument<SmbConnectionPool.Config>(0).password()
		}
		Mockito.mockStatic(CredentialCryptor::class.java).use { cryptors ->
			val cryptor = mock<CredentialCryptor>()
			cryptors.`when`<CredentialCryptor> { CredentialCryptor.getInstance(any()) }.thenReturn(cryptor)
			whenever(cryptor.decrypt("encryptedPassword")).thenThrow(FatalCryptoException(GeneralSecurityException("key lost")))

			assertThrows(UnableToDecryptSmbPasswordException::class.java) { inTest.exists(inTest.resolve("/vault")) }
		}
	}

	@Test
	fun testRenameToExistingNodeReportsTarget() {
		val source = inTest.file(inTest.resolve("/vault"), "a.c9r", null)
		val target = inTest.file(inTest.resolve("/vault"), "b.c9r", null)
		val entry = mock<DiskEntry>()
		whenever(share.open(eq("base\\vault\\a.c9r"), any(), anyOrNull(), any(), eq(SMB2CreateDisposition.FILE_OPEN), any())).thenReturn(entry)
		whenever(entry.rename("base\\vault\\b.c9r", false)).thenThrow(SMBApiException(NtStatus.STATUS_OBJECT_NAME_COLLISION.value, SMB2MessageCommandCode.SMB2_SET_INFO, null))

		val thrown = assertThrows(CloudNodeAlreadyExistsException::class.java) { inTest.move(source, target) }

		assertThat(thrown.message, `is`("b.c9r"))
	}

	@ParameterizedTest
	@ValueSource(booleans = [true, false])
	fun testMoveOpensTheSourceForRenaming(folder: Boolean) {
		val parent = inTest.resolve("/vault")
		val entry = mock<DiskEntry>()
		whenever(share.open(any(), any(), anyOrNull(), any(), any(), any())).thenReturn(entry)

		val moved = if (folder) {
			inTest.move(inTest.folder(parent, "a"), inTest.folder(parent, "b"))
		} else {
			inTest.move(inTest.file(parent, "a", null), inTest.file(parent, "b", null))
		}

		val access = argumentCaptor<Set<AccessMask>>()
		val options = argumentCaptor<Set<SMB2CreateOptions>>()
		verify(share).open(eq("base\\vault\\a"), access.capture(), anyOrNull(), any(), eq(SMB2CreateDisposition.FILE_OPEN), options.capture())
		assertThat(access.firstValue.contains(AccessMask.DELETE), `is`(true))
		assertThat(options.firstValue, `is`(setOf(if (folder) SMB2CreateOptions.FILE_DIRECTORY_FILE else SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)))
		verify(entry).rename("base\\vault\\b", false)
		assertThat(moved.path, `is`("/vault/b"))
		assertThat(moved, instanceOf(if (folder) SmbFolder::class.java else SmbFile::class.java))
	}

	@Test
	fun testCreateAlsoCreatesMissingParents() {
		val folder = inTest.folder(inTest.resolve("/a"), "b")
		whenever(share.mkdir("base\\a\\b")) //
			.thenThrow(SMBApiException(NtStatus.STATUS_OBJECT_PATH_NOT_FOUND.value, SMB2MessageCommandCode.SMB2_CREATE, null)) //
			.thenAnswer { }

		inTest.create(folder)

		val inOrder = inOrder(share)
		inOrder.verify(share).mkdir("base\\a\\b")
		inOrder.verify(share).mkdir("base\\a")
		inOrder.verify(share).mkdir("base\\a\\b")
	}

	@Test
	fun testListSkipsDotEntriesAndMapsFoldersAndFiles() {
		val folder = inTest.resolve("/vault")
		val entries = listOf(entry(".", 0x10), entry("..", 0x10), entry("d", 0x10), entry("a.c9r", 0x20, 3L, 1000L))
		whenever(share.list("base\\vault")).thenReturn(entries)

		val nodes = inTest.list(folder)

		assertThat(nodes.size, `is`(2))
		assertThat(nodes[0], instanceOf(SmbFolder::class.java))
		assertThat(nodes[0].path, `is`("/vault/d"))
		val file = nodes[1] as SmbFile
		assertThat(file.path, `is`("/vault/a.c9r"))
		assertThat(file.size, `is`(3L))
		assertThat(file.modified, `is`(Date(1000L)))
	}

	@Test
	fun testDeleteRemovesFoldersRecursively() {
		inTest.delete(inTest.resolve("/vault/d"))
		inTest.delete(inTest.file(inTest.resolve("/vault"), "a.c9r", null))

		verify(share).rmdir("base\\vault\\d", true)
		verify(share).rm("base\\vault\\a.c9r")
	}

	@Test
	fun testCurrentAccountRequiresConfiguredFolder() {
		whenever(share.folderExists("base")).thenReturn(false)

		assertThrows(NoSuchCloudFileException::class.java) { inTest.currentAccount() }
	}

	@Test
	fun testCurrentAccountReturnsUsername() {
		whenever(share.folderExists("base")).thenReturn(true)

		assertThat(inTest.currentAccount(), `is`("alice"))
	}

	@ParameterizedTest
	@ValueSource(booleans = [true, false])
	fun testWriteUploadsDataAndReturnsServerMetadata(replace: Boolean) {
		val file = inTest.file(inTest.resolve("/vault"), "a.c9r", 3L)
		val uploaded = ByteArrayOutputStream()
		val smbFile = mock<File>()
		val standardInformation = mock<FileStandardInformation>()
		whenever(share.openFile(eq("base\\vault\\a.c9r"), any(), anyOrNull(), any(), any(), anyOrNull())).thenReturn(smbFile)
		whenever(smbFile.outputStream).thenReturn(uploaded)
		whenever(smbFile.getFileInformation(FileBasicInformation::class.java)).thenReturn(basicInformation(5000L))
		whenever(smbFile.getFileInformation(FileStandardInformation::class.java)).thenReturn(standardInformation)
		whenever(standardInformation.endOfFile).thenReturn(3L)

		val written = inTest.write(file, dataSource("abc", Date(5000L)), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, replace, 3L)

		val access = argumentCaptor<Set<AccessMask>>()
		val disposition = argumentCaptor<SMB2CreateDisposition>()
		verify(share).openFile(eq("base\\vault\\a.c9r"), access.capture(), anyOrNull(), any(), disposition.capture(), anyOrNull())
		// writes the content, then sets and reads the metadata
		assertThat(access.firstValue.containsAll(setOf(AccessMask.FILE_WRITE_DATA, AccessMask.FILE_WRITE_ATTRIBUTES, AccessMask.FILE_READ_ATTRIBUTES)), `is`(true))
		assertThat(disposition.firstValue, `is`(if (replace) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_CREATE))
		assertThat(uploaded.toString(), `is`("abc"))
		val modified = argumentCaptor<FileBasicInformation>()
		verify(smbFile).setFileInformation(modified.capture())
		assertThat(modified.firstValue.lastWriteTime.toEpochMillis(), `is`(5000L))
		assertThat(written.size, `is`(3L))
		assertThat(written.modified, `is`(Date(5000L)))
	}

	@Test
	fun testWriteDoesntTouchTheTargetIfTheSourceCantBeOpened() {
		val file = inTest.file(inTest.resolve("/vault"), "a.c9r", 3L)
		val data = mock<DataSource>()
		whenever(data.modifiedDate(any())).thenReturn(Optional.empty())
		whenever(data.open(any())).thenThrow(FileNotFoundException("gone"))

		val thrown = assertThrows(FatalBackendException::class.java) { inTest.write(file, data, ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, true, 3L) }
		assertThat(thrown.cause, instanceOf(FileNotFoundException::class.java))
		verify(share, never()).openFile(any(), any(), anyOrNull(), any(), any(), anyOrNull())
	}

	@Test
	fun testWriteClosesTheSourceIfTheTargetCantBeOpened() {
		val file = inTest.file(inTest.resolve("/vault"), "a.c9r", 3L)
		val source = mock<InputStream>()
		val data = mock<DataSource>()
		whenever(data.modifiedDate(any())).thenReturn(Optional.empty())
		whenever(data.open(any())).thenReturn(source)
		whenever(share.openFile(any(), any(), anyOrNull(), any(), any(), anyOrNull())) //
			.thenThrow(SMBApiException(NtStatus.STATUS_OBJECT_NAME_COLLISION.value, SMB2MessageCommandCode.SMB2_CREATE, null))

		assertThrows(CloudNodeAlreadyExistsException::class.java) { inTest.write(file, data, ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 3L) }
		verify(source).close()
	}

	@Test
	fun testWriteWithoutModifiedDateKeepsTheServersOne() {
		val file = inTest.file(inTest.resolve("/vault"), "dir.c9r", 3L)
		val smbFile = mock<File>()
		val standardInformation = mock<FileStandardInformation>()
		whenever(share.openFile(eq("base\\vault\\dir.c9r"), any(), anyOrNull(), any(), any(), anyOrNull())).thenReturn(smbFile)
		whenever(smbFile.outputStream).thenReturn(ByteArrayOutputStream())
		whenever(smbFile.getFileInformation(FileBasicInformation::class.java)).thenReturn(basicInformation(7000L))
		whenever(smbFile.getFileInformation(FileStandardInformation::class.java)).thenReturn(standardInformation)
		whenever(standardInformation.endOfFile).thenReturn(3L)

		val written = inTest.write(file, dataSource("abc", null), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, true, 3L)

		verify(smbFile, never()).setFileInformation(any())
		assertThat(written.modified, `is`(Date(7000L)))
	}

	@Test
	fun testReadDownloadsData() {
		val file = inTest.file(inTest.resolve("/vault"), "a.c9r", 3L)
		val smbFile = mock<File>()
		whenever(share.openFile(eq("base\\vault\\a.c9r"), any(), anyOrNull(), any(), eq(SMB2CreateDisposition.FILE_OPEN), anyOrNull())).thenReturn(smbFile)
		whenever(smbFile.inputStream).thenReturn(ByteArrayInputStream("abc".toByteArray()))
		val downloaded = ByteArrayOutputStream()

		inTest.read(file, downloaded, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)

		assertThat(downloaded.toString(), `is`("abc"))
	}

	@Test
	fun testRequestsThatDontModifyAnythingAreRetriedAfterConnectionFailures() {
		val folder = inTest.resolve("/vault")

		inTest.exists(folder)
		inTest.list(folder)

		verify(connectionPool, times(2)).withShare<Any?>(any(), eq(Retry.IF_FAILED), any())
	}

	@Test
	fun testModificationsAreOnlyRetriedIfRejected() {
		val folder = inTest.resolve("/vault")

		inTest.create(folder)
		inTest.delete(folder)

		verify(connectionPool, times(2)).withShare<Any?>(any(), eq(Retry.IF_REJECTED), any())
	}

	@Test
	fun testTransfersAreNeverRetried() {
		whenever(connectionPool.withShare<Any?>(any(), any(), any())).thenThrow(SMBRuntimeException("stop"))
		val file = inTest.file(inTest.resolve("/vault"), "a.c9r", 1L)

		assertThrows(Exception::class.java) { inTest.write(file, dataSource("a", null), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, true, 1L) }
		assertThrows(Exception::class.java) { inTest.read(file, ByteArrayOutputStream(), ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD) }

		verify(connectionPool, times(2)).withShare<Any?>(any(), eq(Retry.NEVER), any())
	}

	private fun entry(name: String, attributes: Long, size: Long = 0L, modified: Long = 0L): FileIdBothDirectoryInformation {
		val entry = mock<FileIdBothDirectoryInformation>()
		whenever(entry.fileName).thenReturn(name)
		whenever(entry.fileAttributes).thenReturn(attributes)
		whenever(entry.endOfFile).thenReturn(size)
		whenever(entry.lastWriteTime).thenReturn(FileTime.ofEpochMillis(modified))
		return entry
	}

	private fun basicInformation(lastWrite: Long): FileBasicInformation {
		return FileBasicInformation(FileTime.ofEpochMillis(0), FileTime.ofEpochMillis(0), FileTime.ofEpochMillis(lastWrite), FileTime.ofEpochMillis(0), 0)
	}

	private fun dataSource(content: String, modified: Date?): DataSource {
		val data = mock<DataSource>()
		whenever(data.open(any())).thenReturn(ByteArrayInputStream(content.toByteArray()))
		whenever(data.modifiedDate(any())).thenReturn(if (modified == null) Optional.empty() else Optional.of(modified))
		return data
	}

	companion object {

		@JvmStatic
		fun errors(): List<Arguments> {
			return listOf(
				Arguments.of(SMBApiException(NtStatus.STATUS_LOGON_FAILURE.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null), WrongCredentialsException::class.java),
				Arguments.of(SMBApiException(NtStatus.STATUS_PASSWORD_EXPIRED.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null), WrongCredentialsException::class.java),
				Arguments.of(SMBApiException(NtStatus.STATUS_ACCOUNT_DISABLED.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null), WrongCredentialsException::class.java),
				Arguments.of(SMBApiException(NtStatus.STATUS_ACCESS_DENIED.value, SMB2MessageCommandCode.SMB2_SESSION_SETUP, null), WrongCredentialsException::class.java),
				Arguments.of(SMBApiException(NtStatus.STATUS_ACCESS_DENIED.value, SMB2MessageCommandCode.SMB2_TREE_CONNECT, null), WrongCredentialsException::class.java),
				Arguments.of(SMB2GuestSigningRequiredException(), WrongCredentialsException::class.java),
				Arguments.of(SMBApiException(NtStatus.STATUS_NO_SUCH_FILE.value, SMB2MessageCommandCode.SMB2_QUERY_DIRECTORY, null), NoSuchCloudFileException::class.java),
				Arguments.of(SMBApiException(NtStatus.STATUS_BAD_NETWORK_NAME.value, SMB2MessageCommandCode.SMB2_TREE_CONNECT, null), NoSuchCloudFileException::class.java),
				Arguments.of(SMBApiException(NtStatus.STATUS_OBJECT_NAME_NOT_FOUND.value, SMB2MessageCommandCode.SMB2_CREATE, null), NoSuchCloudFileException::class.java),
				Arguments.of(SMBApiException(NtStatus.STATUS_OBJECT_PATH_NOT_FOUND.value, SMB2MessageCommandCode.SMB2_CREATE, null), NoSuchCloudFileException::class.java),
				Arguments.of(SMBRuntimeException(TransportException("connection reset")), NetworkConnectionException::class.java),
				Arguments.of(SMBRuntimeException(TimeoutException()), NetworkConnectionException::class.java),
				Arguments.of(SMBRuntimeException(LocalNetworkAccessDeniedException("nas")), LocalNetworkPermissionMissingException::class.java)
			)
		}
	}
}
