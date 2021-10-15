package org.cryptomator.domain.repository

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.OutputStream

/**
 *
 *
 * An interface to retrieve the contents of a cloud.
 *
 *
 * A CloudContentRepository will throw [AuthenticationExceptions][AuthenticationException]
 * from any operation if AuthenticationExceptions occur to allow correct handling in the UI.
 */
interface CloudContentRepository<CloudType : Cloud, NodeType : CloudNode, DirType : CloudFolder, FileType : CloudFile> {

	@Throws(BackendException::class)
	fun root(cloud: CloudType): DirType

	@Throws(BackendException::class)
	fun resolve(cloud: CloudType, path: String): DirType

	@Throws(BackendException::class)
	fun file(parent: DirType, name: String): FileType

	@Throws(BackendException::class)
	fun file(parent: DirType, name: String, size: Long?): FileType

	@Throws(BackendException::class)
	fun folder(parent: DirType, name: String): DirType

	@Throws(BackendException::class)
	fun exists(node: NodeType): Boolean

	@Throws(BackendException::class)
	fun list(folder: DirType): List<NodeType>

	/**
	 * Creates a cloud folder and maybe intermediate directories.
	 *
	 * @return created cloud folder (migth be different from target)
	 * @throws org.cryptomator.domain.exception.CloudNodeAlreadyExistsException If a cloud node with the same folder name already exists
	 */
	@Throws(BackendException::class)
	fun create(folder: DirType): DirType

	/**
	 * @return moved cloud folder (might be different from target)
	 * @throws org.cryptomator.domain.exception.CloudNodeAlreadyExistsException If a cloud node with the same target name already exists
	 */
	@Throws(BackendException::class)
	fun move(source: DirType, target: DirType): DirType

	/**
	 * @return moved cloud file (might be different from target)
	 * @throws org.cryptomator.domain.exception.CloudNodeAlreadyExistsException If a cloud node with the same target name already exists
	 */
	@Throws(BackendException::class)
	fun move(source: FileType, target: FileType): FileType

	/**
	 * @throws org.cryptomator.domain.exception.CloudNodeAlreadyExistsException If a cloud node with the same file name already exists
	 */
	@Throws(BackendException::class)
	fun write(file: FileType, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): FileType

	@Throws(BackendException::class)
	fun read(file: FileType, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>)

	@Throws(BackendException::class)
	fun delete(node: NodeType)

	@Throws(BackendException::class)
	fun checkAuthenticationAndRetrieveCurrentAccount(cloud: CloudType): String

	/**
	 * Performs a logout. After a call to this method further usage of this cloud will cause [AuthenticationFailedExceptions][org.cryptomator.cryptolib.api.AuthenticationFailedException].
	 */
	@Throws(BackendException::class)
	fun logout(cloud: CloudType)
}
