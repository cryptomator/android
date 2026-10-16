package org.cryptomator.domain.repository

import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.UploadState

/**
 * Changes the nodes of unlocked vaults through the cache that the documents provider answers from, so that the app,
 * other apps and the cloud see the same state. A change is applied to the cache right away and reaches the cloud in
 * the background; its [VaultDocumentChange] tells how that ended.
 *
 * A name that is taken in the cache fails the change with a
 * [CloudNodeAlreadyExistsException][org.cryptomator.domain.exception.CloudNodeAlreadyExistsException], or a
 * [CloudNodeIsFolderException][org.cryptomator.domain.exception.CloudNodeIsFolderException] if a file would replace a
 * folder. A name that is only taken in the cloud gets a conflict name instead, e.g. `notes (1A2B3).txt`.
 */
interface VaultDocumentRepository {

	/**
	 * @return where [node] is in its vault, or `null` if it is not inside a vault
	 */
	fun pathOf(node: CloudNode): VaultPath?

	@Throws(BackendException::class)
	fun createFolder(folder: VaultPath, name: String): VaultDocumentChange<CloudFolder>

	@Throws(BackendException::class)
	fun rename(path: VaultPath, name: String): VaultDocumentChange<CloudNode>

	@Throws(BackendException::class)
	fun move(path: VaultPath, folder: VaultPath): VaultDocumentChange<CloudNode>

	/**
	 * Stops a running upload of a deleted file, but keeps its local copy until the cloud has accepted the deletion, so
	 * that a rejected deletion brings the file back as it was.
	 */
	@Throws(BackendException::class)
	fun delete(path: VaultPath): VaultDocumentChange<Unit>

	/**
	 * Copies [data] into the cache and uploads it from there, so that the file is kept until it has been uploaded.
	 *
	 * @param requireListing whether [folder] has to be listed from the cloud first if its listing is outdated, so that
	 * a name taken in the cloud is noticed; otherwise the cache is used if listing fails
	 */
	@Throws(BackendException::class)
	fun importFile(folder: VaultPath, name: String, data: DataSource, replace: Boolean, requireListing: Boolean, progressAware: ProgressAware<UploadState>): VaultDocumentImport

	/**
	 * Retries the uploads of [vault] that failed or were interrupted, except those that are running.
	 */
	fun resumeUploads(vault: Vault)
}

fun interface VaultDocumentChange<T> {

	/**
	 * Waits until the change has reached the cloud.
	 *
	 * @throws BackendException why the cloud rejected the change. A rejected create, move or delete has been undone in
	 * the cache, a rejected upload stays pending for a retry.
	 */
	@Throws(BackendException::class)
	fun await(): VaultDocumentChangeResult<T>
}

/**
 * @param path where the node ended up, which differs from the requested path if the name was taken in the cloud
 * @param node the node in the cloud
 */
class VaultDocumentChangeResult<T>(val path: VaultPath, val node: T)

interface VaultDocumentImport : VaultDocumentChange<CloudFile> {

	/**
	 * Stops the upload and drops the import: a new file is removed, a replaced one goes back to its previous content.
	 * Nothing happens if the upload is done or the file has been written again since.
	 */
	fun cancel()
}
