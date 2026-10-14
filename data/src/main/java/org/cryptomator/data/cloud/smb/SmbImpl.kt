package org.cryptomator.data.cloud.smb

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.common.SMBRuntimeException
import com.hierynomus.smbj.session.SMB2GuestSigningRequiredException
import com.hierynomus.smbj.share.DiskShare
import org.cryptomator.data.cloud.smb.SmbConnectionPool.Retry
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.LocalNetworkAccessDeniedException
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.SmbCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.UnableToDecryptSmbPasswordException
import org.cryptomator.domain.exception.authentication.LocalNetworkPermissionMissingException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.ExceptionUtil
import org.cryptomator.util.crypto.CredentialCryptor
import org.cryptomator.util.crypto.FatalCryptoException
import java.io.IOException
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.TimeoutException

internal class SmbImpl(private val cloud: SmbCloud, private val connectionPool: SmbConnectionPool, private val context: Context) {

	private val location = SmbLocation.parse(cloud.url()) ?: throw FatalBackendException("Invalid SMB url")
	private val connectionConfig = SmbConnectionPool.Config(location, cloud.username(), cloud.password(), ::decryptedPassword)
	private val root = RootSmbFolder(cloud)

	fun root(): SmbFolder {
		return root
	}

	fun resolve(path: String): SmbFolder {
		var folder: SmbFolder = root
		path.split('/').filter { it.isNotEmpty() }.forEach { name ->
			folder = folder(folder, name)
		}
		return folder
	}

	fun file(parent: SmbFolder, name: String, size: Long?): SmbFile {
		return SmbFile(parent, name, parent.path + '/' + name, size, null)
	}

	fun folder(parent: SmbFolder, name: String): SmbFolder {
		return SmbFolder(parent, name, parent.path + '/' + name)
	}

	@Throws(BackendException::class)
	fun exists(node: SmbNode): Boolean {
		return withShare(node, Retry.IF_FAILED) { share ->
			when (node) {
				is SmbFolder -> share.folderExists(pathOf(node))
				else -> share.fileExists(pathOf(node))
			}
		}
	}

	@Throws(BackendException::class)
	fun list(folder: SmbFolder): List<SmbNode> {
		return withShare(folder, Retry.IF_FAILED) { share ->
			share.list(pathOf(folder)) //
				.filterNot { it.fileName == "." || it.fileName == ".." } //
				.map { info ->
					if (EnumWithValue.EnumUtils.isSet(info.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)) {
						folder(folder, info.fileName)
					} else {
						SmbFile(folder, info.fileName, folder.path + '/' + info.fileName, info.endOfFile, info.lastWriteTime.toDate())
					}
				}
		}
	}

	@Throws(BackendException::class)
	fun create(folder: SmbFolder): SmbFolder {
		val parent = folder.parent ?: return folder
		try {
			withShare(folder, Retry.IF_REJECTED) { share -> share.mkdir(pathOf(folder)) }
		} catch (e: NoSuchCloudFileException) {
			// SMB doesn't create intermediate directories
			create(parent)
			withShare(folder, Retry.IF_REJECTED) { share -> share.mkdir(pathOf(folder)) }
		}
		return folder
	}

	@Throws(BackendException::class)
	fun move(source: SmbFolder, target: SmbFolder): SmbFolder {
		rename(source, target)
		return SmbFolder(target.parent, target.name, target.path)
	}

	@Throws(BackendException::class)
	fun move(source: SmbFile, target: SmbFile): SmbFile {
		rename(source, target)
		return SmbFile(target.parent, target.name, target.path, source.size, source.modified)
	}

	private fun rename(source: SmbNode, target: SmbNode) {
		withShare(source, Retry.IF_REJECTED, target) { share ->
			val createOptions = EnumSet.of(if (source is SmbFolder) SMB2CreateOptions.FILE_DIRECTORY_FILE else SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
			share.open(pathOf(source), EnumSet.of(AccessMask.DELETE, AccessMask.FILE_READ_ATTRIBUTES), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, createOptions).use { entry ->
				entry.rename(pathOf(target), false)
			}
		}
	}

	@Throws(BackendException::class)
	fun write(file: SmbFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): SmbFile {
		progressAware.onProgress(Progress.started(UploadState.upload(file)))
		val disposition = if (replace) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_CREATE
		val modified = data.modifiedDate(context).orElse(null)
		val written = withShare(file, Retry.NEVER) { share ->
			share.openFile(
				pathOf(file), //
				EnumSet.of(AccessMask.FILE_WRITE_DATA, AccessMask.FILE_READ_ATTRIBUTES, AccessMask.FILE_WRITE_ATTRIBUTES), //
				null, //
				EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), //
				disposition, //
				null
			).use { smbFile ->
				val inputStream = data.open(context) ?: throw FatalBackendException("InputStream shouldn't be null")
				object : TransferredBytesAwareInputStream(inputStream) {
					override fun bytesTransferred(transferred: Long) {
						progressAware.onProgress( //
							Progress.progress(UploadState.upload(file)) //
								.between(0) //
								.and(size) //
								.withValue(transferred)
						)
					}
				}.use { input -> smbFile.outputStream.use { output -> CopyStream.copyStreamToStream(input, output) } }
				if (modified != null) {
					smbFile.setFileInformation(FileBasicInformation(FileBasicInformation.DONT_SET, FileBasicInformation.DONT_SET, FileTime.fromDate(modified), FileBasicInformation.DONT_SET, 0))
				}
				val basicInformation = smbFile.getFileInformation(FileBasicInformation::class.java)
				val standardInformation = smbFile.getFileInformation(FileStandardInformation::class.java)
				SmbFile(file.parent, file.name, file.path, standardInformation.endOfFile, basicInformation.lastWriteTime.toDate())
			}
		}
		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return written
	}

	@Throws(BackendException::class)
	fun read(file: SmbFile, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		withShare(file, Retry.NEVER) { share ->
			share.openFile(pathOf(file), EnumSet.of(AccessMask.FILE_READ_DATA), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null).use { smbFile ->
				smbFile.inputStream.use { input ->
					object : TransferredBytesAwareOutputStream(data) {
						override fun bytesTransferred(transferred: Long) {
							progressAware.onProgress( //
								Progress.progress(DownloadState.download(file)) //
									.between(0) //
									.and(file.size ?: Long.MAX_VALUE) //
									.withValue(transferred)
							)
						}
					}.use { output -> CopyStream.copyStreamToStream(input, output) }
				}
			}
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	@Throws(BackendException::class)
	fun delete(node: SmbNode) {
		withShare(node, Retry.IF_REJECTED) { share ->
			when (node) {
				is SmbFolder -> share.rmdir(pathOf(node), true)
				else -> share.rm(pathOf(node))
			}
		}
	}

	@Throws(BackendException::class)
	fun currentAccount(): String {
		// connecting the share authenticates and verifies that the share exists
		withShare(root, Retry.IF_FAILED) { share ->
			if (!share.folderExists(pathOf(root))) {
				throw NoSuchCloudFileException(pathOf(root))
			}
		}
		return cloud.username()
	}

	fun logout() {
		connectionPool.logout(connectionConfig)
	}

	private fun pathOf(node: SmbNode): String {
		return location.pathOf(node.path)
	}

	/**
	 * Runs [action] on the connected share and maps SMB errors to domain exceptions.
	 */
	private fun <T> withShare(node: SmbNode, retry: Retry, target: SmbNode? = null, action: (DiskShare) -> T): T {
		return try {
			connectionPool.withShare(connectionConfig, retry, action)
		} catch (e: Exception) {
			throw map(e, node, target)
		}
	}

	private fun decryptedPassword(): String {
		return try {
			CredentialCryptor.getInstance(context).decrypt(cloud.password())
		} catch (e: FatalCryptoException) {
			throw UnableToDecryptSmbPasswordException(e)
		}
	}

	private fun map(e: Exception, node: SmbNode, target: SmbNode?): Exception {
		return when {
			ExceptionUtil.contains(e, LocalNetworkAccessDeniedException::class.java) -> LocalNetworkPermissionMissingException(cloud)
			hasStatus(e, NtStatus.STATUS_LOGON_FAILURE, NtStatus.STATUS_PASSWORD_EXPIRED, NtStatus.STATUS_ACCOUNT_DISABLED) -> WrongCredentialsException(cloud)
			hasStatus(e, NtStatus.STATUS_ACCESS_DENIED) && failedDuringLogon(e) -> WrongCredentialsException(cloud)
			// the server mapped the user to its guest account, but requires signing, which a guest session can't do
			ExceptionUtil.contains(e, SMB2GuestSigningRequiredException::class.java) -> WrongCredentialsException(cloud)
			hasStatus(e, NtStatus.STATUS_BAD_NETWORK_NAME) -> NoSuchCloudFileException(location.share)
			hasStatus(e, NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.STATUS_NO_SUCH_FILE) -> NoSuchCloudFileException(node.name)
			hasStatus(e, NtStatus.STATUS_OBJECT_NAME_COLLISION) -> CloudNodeAlreadyExistsException((target ?: node).name)
			e is BackendException -> e
			ExceptionUtil.contains(e, IOException::class.java) || ExceptionUtil.contains(e, TimeoutException::class.java) -> NetworkConnectionException(e)
			e is SMBRuntimeException -> FatalBackendException(e)
			else -> e
		}
	}

	private fun failedDuringLogon(e: Exception): Boolean {
		val failedCommand = failedCommand(e)
		return failedCommand == SMB2MessageCommandCode.SMB2_SESSION_SETUP || failedCommand == SMB2MessageCommandCode.SMB2_TREE_CONNECT
	}
}
