package org.cryptomator.domain.repository

import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.OutputStream

/**
 * Changes the nodes of unlocked vaults through the cache that the documents provider answers from, so that the app,
 * other apps and the cloud see the same state. A change is applied to the cache right away and reaches the cloud in
 * the background; its [VaultDocumentChange] tells how that ended.
 *
 * A name that is taken in the cache fails the change with a
 * [CloudNodeAlreadyExistsException][org.cryptomator.domain.exception.CloudNodeAlreadyExistsException], or a
 * [CloudNodeIsFolderException][org.cryptomator.domain.exception.CloudNodeIsFolderException] if a file would replace a
 * folder. A name that is only taken in the cloud gets a conflict name instead, e.g. `notes (1A2B3).txt`.
 *
 * A change the cloud rejects and an upload that fails are reported to the listeners, see
 * [VaultDocumentListener.onChangeFailed]. The change's `userInitiated` tells them whether the user asked for it.
 */
interface VaultDocumentRepository {

	/**
	 * @return where [node] is in its vault, or `null` if it is not inside a vault
	 */
	fun pathOf(node: CloudNode): VaultPath?

	/**
	 * The children of [folder] as the cache knows them, as [CachedVaultFile][org.cryptomator.domain.CachedVaultFile]s and
	 * [CachedVaultFolder][org.cryptomator.domain.CachedVaultFolder]s. A folder whose listing is due is listed from the
	 * cloud in the background, and the listeners are told once it is done. With [forceRefresh], it is listed right away,
	 * and a failure is thrown, unless the listing only kept being overtaken by changes to the folder, which the cache
	 * holds already.
	 */
	@Throws(BackendException::class)
	fun list(folder: CloudFolder, forceRefresh: Boolean): VaultListing

	@Throws(BackendException::class)
	fun createFolder(folder: VaultPath, name: String, userInitiated: Boolean): VaultDocumentChange<CloudFolder>

	@Throws(BackendException::class)
	fun rename(path: VaultPath, name: String, userInitiated: Boolean): VaultDocumentChange<CloudNode>

	@Throws(BackendException::class)
	fun move(path: VaultPath, folder: VaultPath, userInitiated: Boolean): VaultDocumentChange<CloudNode>

	/**
	 * Stops a running upload of a deleted file, but keeps its local copy until the cloud has accepted the deletion, so
	 * that a rejected deletion brings the file back as it was.
	 */
	@Throws(BackendException::class)
	fun delete(path: VaultPath, userInitiated: Boolean): VaultDocumentChange<Unit>

	/**
	 * Copies [data] into the cache and uploads it from there, so that the file is kept until it has been uploaded.
	 *
	 * @param requireListing whether [folder] has to be listed from the cloud first if its listing is outdated, so that
	 * a name taken in the cloud is noticed; otherwise the cache is used if listing fails
	 */
	@Throws(BackendException::class)
	fun importFile(folder: VaultPath, name: String, data: DataSource, replace: Boolean, requireListing: Boolean, userInitiated: Boolean, progressAware: ProgressAware<UploadState>): VaultDocumentImport

	/**
	 * Writes the file at [path] to [target] from its local copy, downloading the copy first if it isn't current.
	 */
	@Throws(BackendException::class)
	fun read(path: VaultPath, target: OutputStream, progressAware: ProgressAware<DownloadState>)

	/**
	 * Makes the local copy of the file at [path] current, downloading it if needed, so that the app can open it through
	 * the documents provider without waiting for the cloud.
	 *
	 * @return the documentId of the file in the documents provider
	 */
	@Throws(BackendException::class)
	fun open(path: VaultPath, progressAware: ProgressAware<DownloadState>): String

	/**
	 * Called right before another app gets the file opened with [open] to edit it. Until [endExternalEditing], the
	 * opened version stays the base of the file's saves, so that a save becomes a conflict copy if the file changed in the
	 * cloud meanwhile. A save while the vault is locked is kept and uploaded after the next unlock.
	 */
	fun startExternalEditing(documentId: String)

	fun endExternalEditing(documentId: String)

	/**
	 * Retries the uploads of [vault] that failed or were interrupted and aren't running. Only the first call for [vault]
	 * after any vault was locked or unlocked does so; later ones do nothing until the next lock or unlock.
	 */
	fun resumeUploadsOnce(vault: Vault)

	/**
	 * Retries the uploads of [paths] in [vault] that failed or were interrupted and aren't running, since the user asked
	 * for it.
	 */
	fun retryUploads(vault: Vault, paths: List<VaultPath>)

	/**
	 * Makes previews of the images among [files] in the background, the most recently asked for first. The listeners are
	 * told about each one that is done.
	 */
	fun ensureThumbnails(files: List<CloudFile>)

	/**
	 * Deletes the local copies and thumbnails that can be made again: copies with a pending upload, of a file being
	 * deleted, in use, or of a file an external editor may write are kept.
	 */
	fun clearCache()

	/**
	 * Deletes the local copies that belong to no cached file anymore, e.g. those of a removed vault, and the thumbnails
	 * of nodes that are gone or whose content changed, in the background. Copies with a pending upload or in use are kept.
	 */
	fun evictCopies()

	/**
	 * The size in bytes of the local copies and thumbnails.
	 */
	fun cacheSize(): Long

	fun addListener(listener: VaultDocumentListener)

	fun removeListener(listener: VaultDocumentListener)
}

/**
 * @param isLoading whether the folder is being listed from the cloud
 * @param error why the folder could not be listed if the cache has no valid listing of it. An authentication the
 * listing needs is reported even if the cache has an earlier listing.
 */
class VaultListing(val nodes: List<CloudNode>, val isLoading: Boolean, val error: Throwable?)

interface VaultDocumentChange<T> {

	/**
	 * The changed node as the cache shows it now, also if it was renamed or moved since. `null` if the node is gone, and
	 * always for a deletion.
	 */
	fun cachedNode(): T?

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
