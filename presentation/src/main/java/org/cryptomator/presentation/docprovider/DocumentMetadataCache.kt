package org.cryptomator.presentation.docprovider

import android.provider.DocumentsContract.Document
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.cloud.crypto.CryptoFolder
import org.cryptomator.data.cloud.crypto.CryptoSymlink
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.EmptyDataSource
import org.cryptomator.domain.usecases.cloud.FileBasedDataSource
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.named
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import timber.log.Timber

private val LOG = Timber.named("DocumentMetadataCache")

private val REFRESH_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30)

private const val MAX_NAME_COLLISION_RETRIES = 5

private const val MAX_LISTING_ATTEMPTS = 3

/**
 * Answers metadata queries from the persisted cache. The cached children of a folder are returned right away. A folder
 * that has never been listed, or not within [REFRESH_INTERVAL_MILLIS], is listed from the cloud in the background.
 *
 * Changes are applied to the cache first and to the cloud afterwards, ordered by the [scheduler] like in the iOS app.
 * A change is applied to the cache and handed to the scheduler under one lock, so requests are made in the order of
 * the changes. A cloud request uses the path the node had in the cache when the request was made. That is where the
 * node is in the cloud once the requests before it are done, unless one of them had to rename the node because its
 * name was taken. A listing during which a change to the folder reached the cloud is repeated, so that it doesn't
 * overwrite what the change stored.
 *
 * A create, move or delete that the cloud rejects is dropped from the cache, and the affected folders are listed again.
 * A failed upload stays pending instead. A change that fails because its name is taken in the cloud is renamed and
 * retried. [onChanged] is called for every folder whose children a change or a background listing changed.
 *
 * Nodes are identified by documentIds made of the vault id and the id of their cache entry, which stays the same when
 * they are renamed or moved. Log messages refer to nodes by their documentId rather than by their cleartext name.
 */
class DocumentMetadataCache(
	private val dao: DocumentMetadataDao,
	private val scheduler: CloudTaskScheduler,
	private val onChanged: (VaultPath) -> Unit,
	private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {

	private val changeLock = Any()

	/**
	 * How many changes reached the cloud, by the folder whose children they changed; guarded by itself.
	 */
	private val finishedChanges = HashMap<VaultPath, Long>()
	private val refreshing: MutableSet<VaultPath> = ConcurrentHashMap.newKeySet()
	private val failedListings = ConcurrentHashMap<VaultPath, FailedListing>()

	init {
		dao.loadInterruptedChanges().forEach(dao::discardPendingChange)
	}

	/**
	 * [error] tells why a folder that has never been listed could not be listed.
	 */
	class Children(val documents: List<DocumentMetadataEntity>, val isLoading: Boolean, val error: Throwable?)

	private class FailedListing(val failedAt: Long, val cause: Throwable)

	private class FolderKeptChangingException(folderId: String) : BackendException("$folderId kept changing while it was listed")

	/**
	 * Resolves a documentId: `<vaultId>` for the root of a vault, `<vaultId>:<id>` for a node in it.
	 */
	fun path(documentId: String): VaultPath {
		val vaultId = documentId.substringBefore(':').toLongOrNull() ?: throw IllegalArgumentException("Illegal documentId: $documentId")
		val vault = vaultById(vaultId) ?: throw FileNotFoundException("Unknown vault: $vaultId")
		val id = documentId.substringAfter(':', "").ifEmpty { return VaultPath(vault) }
		val metadata = id.toLongOrNull()?.let { dao.loadById(it) }?.takeIf { it.vaultId == vaultId && !it.isDeleting } ?: throw FileNotFoundException("Unknown document: $documentId")
		return VaultPath(vault, metadata.path)
	}

	fun documentId(path: VaultPath): String? {
		return if (path.isRoot) "${path.vault.id}" else dao.load(path.vault.id, path.path)?.let { documentId(it) }
	}

	fun documentId(metadata: DocumentMetadataEntity): String {
		return documentId(metadata.vaultId, requireNotNull(metadata.id))
	}

	private fun documentId(vaultId: Long, id: Long): String {
		return "$vaultId:$id"
	}

	/**
	 * Identifies [path] in log and exception messages without revealing its cleartext name.
	 */
	fun logId(path: VaultPath): String {
		return documentId(path) ?: "${path.vault.id}:?"
	}

	fun children(folder: VaultPath): Children {
		val lastEnumeratedAt = lastEnumeratedAt(folder)
		if (isListingDue(lastEnumeratedAt)) {
			refreshInBackground(folder, force = false)
		}
		val isLoading = folder in refreshing
		val error = failedListings[folder]?.takeIf { lastEnumeratedAt == null && !isLoading }?.cause
		return Children(dao.loadChildren(folder.vault.id, folder.path), isLoading, error)
	}

	fun document(path: VaultPath): DocumentMetadataEntity? {
		dao.load(path.vault.id, path.path)?.let { cached -> return cached.takeUnless { it.isDeleting } }
		val parent = requireNotNull(path.parent)
		if (!isListingDue(lastEnumeratedAt(parent))) {
			return null
		}
		enumerate(parent)
		return loadVisible(path)
	}

	fun requireDocument(path: VaultPath): DocumentMetadataEntity {
		return document(path) ?: throw FileNotFoundException("Unknown document: ${logId(path)}")
	}

	/**
	 * Like [document], but lists the parent folder from the cloud first if that has not happened recently. If listing
	 * fails, e.g. because there is no connection, the cached metadata is used.
	 */
	fun currentDocument(path: VaultPath): DocumentMetadataEntity? {
		val parent = requireNotNull(path.parent)
		if (isListingDue(lastEnumeratedAt(parent))) {
			try {
				enumerate(parent)
			} catch (e: BackendException) {
				LOG.w(e, "Listing ${logId(parent)} failed, using cached metadata")
			}
		}
		return loadVisible(path)
	}

	fun requireCurrentDocument(path: VaultPath): DocumentMetadataEntity {
		return currentDocument(path) ?: throw FileNotFoundException("Unknown document: ${logId(path)}")
	}

	fun isKnownEmpty(folder: VaultPath): Boolean {
		return lastEnumeratedAt(folder) != null && !dao.hasChildren(folder.vault.id, folder.path)
	}

	/**
	 * Lists [folder] from the cloud in the background, even if it has been listed recently or its last listing failed.
	 */
	fun refresh(folder: VaultPath) {
		refreshInBackground(folder, force = true)
	}

	/**
	 * @return the documentId of the new node
	 */
	fun create(folder: VaultPath, name: String, isFolder: Boolean): String = synchronized(changeLock) {
		requireFolder(folder)
		val path = unusedPath(folder, name)
		val id = dao.insert(DocumentMetadataEntity().apply {
			vaultId = path.vault.id
			this.path = path.path
			parentPath = folder.path
			this.name = path.name
			mimeType = mimeType(isFolder, path.name)
			size = if (isFolder) null else 0L
			lastEnumeratedAt = if (isFolder) currentTimeMillis() else null
			status = DocumentMetadataEntity.STATUS_CREATING
		})
		onChanged(folder)
		applyInBackground(path.vault, id, scheduler.write(listOf(path)) {
			retryingNameCollisions(id, path) { current ->
				if (isFolder) {
					contentRepository.create(contentRepository.folder(parentFolder(current), current.name))
					recordChange(folder) { dao.markCreated(id, null, null) }
				} else {
					val createdFile = contentRepository.write(contentRepository.file(parentFolder(current), current.name, 0), EmptyDataSource, ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 0)
					recordChange(folder) { dao.markCreated(id, createdFile.size, createdFile.modified?.time) }
				}
			}
		})
		documentId(path.vault.id, id)
	}

	fun move(source: VaultPath, folder: VaultPath, name: String) = synchronized(changeLock) {
		val metadata = requireDocument(source)
		requireFolder(folder)
		val target = unusedPath(folder, name)
		val mimeType = mimeType(metadata.isFolder, target.name)
		dao.move(source.vault.id, source.path, target.path, folder.path, target.name, mimeType)
		val sourceFolder = requireNotNull(source.parent)
		onChanged(sourceFolder)
		if (folder != sourceFolder) {
			onChanged(folder)
		}
		val id = requireNotNull(metadata.id)
		applyInBackground(source.vault, id, scheduler.write(listOf(source, target)) {
			retryingNameCollisions(id, target) { current ->
				if (metadata.isFolder) {
					contentRepository.move(contentRepository.folder(parentFolder(source), source.name), contentRepository.folder(parentFolder(current), current.name))
				} else {
					contentRepository.move(contentRepository.file(parentFolder(source), source.name), contentRepository.file(parentFolder(current), current.name))
				}
				recordChange(sourceFolder, folder) { dao.markMoved(id, current.path) }
			}
		})
	}

	/**
	 * @return the documentIds of the descendants that are deleted along with [path]
	 */
	fun delete(path: VaultPath): List<String> = synchronized(changeLock) {
		val metadata = requireDocument(path)
		val descendantIds = dao.loadDescendantIds(path.vault.id, path.path).map { documentId(path.vault.id, it) }
		dao.markDeletingWithDescendants(path.vault.id, path.path)
		onChanged(requireNotNull(path.parent))
		val id = requireNotNull(metadata.id)
		applyInBackground(path.vault, id, scheduler.write(listOf(path)) {
			contentRepository.delete(if (metadata.isFolder) contentRepository.folder(parentFolder(path), path.name) else contentRepository.file(parentFolder(path), path.name))
			recordChange(requireNotNull(path.parent)) { dao.loadById(id)?.let { dao.deleteSubtree(it.vaultId, it.path) } }
		})
		descendantIds
	}

	/**
	 * Uploads the local copy of a file that has been written. If the upload fails, the file stays marked as uploading.
	 * That keeps the local copy and lists it in [pendingUploads] for a retry. If the file is written again before the
	 * upload starts, only the later write is uploaded.
	 *
	 * [baseLastModified] tells which version in the cloud the local copy is based on, and defaults to the cached
	 * modification date. If the file changed in the cloud since, the local copy is uploaded as a conflict copy. A file
	 * not based on any version in the cloud is never uploaded over an existing one, but renamed like a new file whose
	 * name is taken.
	 *
	 * [onUploaded] receives where the file is in the cache and where the local copy has been uploaded to.
	 */
	fun upload(path: VaultPath, localFile: File, baseLastModified: (VaultPath) -> Long?, onUploaded: (path: VaultPath, uploadedPath: VaultPath, lastModified: Long?) -> Unit) {
		synchronized(changeLock) {
			dao.markUploading(path.vault.id, path.path)
			val metadata = dao.load(path.vault.id, path.path) ?: throw FileNotFoundException("Unknown document: ${logId(path)}")
			val upload = Upload(path, requireNotNull(metadata.id), metadata.uploadGeneration, localFile, onUploaded)
			onChanged(requireNotNull(path.parent))
			scheduler.upload(path) { uploadNow(upload, baseLastModified) }
		}
	}

	/**
	 * Marks [path] as waiting for an upload of its local copy that [upload] will be called for later. The copy is kept,
	 * and if the upload never comes, [pendingUploads] lists it for a retry.
	 */
	fun markPendingUpload(path: VaultPath) {
		synchronized(changeLock) {
			dao.markUploading(path.vault.id, path.path)
			onChanged(requireNotNull(path.parent))
		}
	}

	/**
	 * Gives up the pending upload of [path], whose local copy is gone, and lists its folder again.
	 */
	fun abandonUpload(path: VaultPath) {
		val metadata = dao.load(path.vault.id, path.path)?.takeIf { it.isUploading } ?: return
		dao.markSynced(requireNotNull(metadata.id))
		dao.updateLastEnumeratedAt(path.vault.id, metadata.parentPath, null)
		onChanged(requireNotNull(path.parent))
	}

	private inner class Upload(val path: VaultPath, val id: Long, val generation: Long, val localFile: File, val onUploaded: (path: VaultPath, uploadedPath: VaultPath, lastModified: Long?) -> Unit) {

		val vault: Vault
			get() = path.vault

		/**
		 * The upload's node as it is in the cache now, unless the node has been deleted or written again since.
		 */
		fun current(): DocumentMetadataEntity? {
			return dao.loadById(id)?.takeIf { it.uploadGeneration == generation && !it.isDeleting }
		}
	}

	private fun uploadNow(upload: Upload, baseLastModified: (VaultPath) -> Long?) {
		val metadata = upload.current() ?: return
		val current = VaultPath(upload.vault, metadata.path)
		try {
			val cloudPath = upload.path
			val remoteFile = contentRepository.metadata(contentRepository.file(parentFolder(cloudPath), cloudPath.name))
			val base = baseLastModified(current) ?: metadata.lastModified
			if (remoteFile == null || base == null) {
				retryingNameCollisions(upload.id, cloudPath) { uploadTo(upload, it, replace = false) }
			} else if (isSameSecond(remoteFile.modified?.time, base)) {
				uploadTo(upload, cloudPath, replace = true)
			} else {
				uploadConflictCopy(upload, cloudPath, current, remoteFile)
			}
		} catch (e: Exception) {
			LOG.e(e, "Uploading ${documentId(metadata)} failed")
		}
		onChanged(requireNotNull(current.parent))
	}

	private fun uploadTo(upload: Upload, cloudPath: VaultPath, replace: Boolean) {
		val uploadedFile = write(cloudPath, upload.localFile, replace)
		recordChange(requireNotNull(cloudPath.parent)) {
			val path = VaultPath(upload.vault, (dao.loadById(upload.id) ?: return).path)
			dao.markUploaded(upload.id, uploadedFile.size ?: upload.localFile.length(), uploadedFile.modified?.time, upload.generation)
			upload.onUploaded(path, path, uploadedFile.modified?.time)
		}
	}

	/**
	 * Keeps both versions of a file that changed locally and in the cloud: the local one is uploaded as a conflict copy,
	 * the original takes the cloud version.
	 */
	private fun uploadConflictCopy(upload: Upload, cloudPath: VaultPath, current: VaultPath, remoteFile: CloudFile) {
		val conflictName = unusedConflictPath(current).name
		LOG.i("${documentId(upload.vault.id, upload.id)} changed in the cloud, uploading the local changes as a conflict copy")
		val uploadedFile = write(requireNotNull(cloudPath.parent).resolve(conflictName), upload.localFile, false)
		recordChange(requireNotNull(cloudPath.parent)) {
			val path = VaultPath(upload.vault, (dao.loadById(upload.id) ?: return).path)
			val conflictPath = requireNotNull(path.parent).resolve(conflictName)
			dao.store(entity(upload.vault, uploadedFile, conflictPath))
			dao.markUploaded(upload.id, remoteFile.size, remoteFile.modified?.time, upload.generation)
			upload.onUploaded(path, conflictPath, uploadedFile.modified?.time)
		}
	}

	/**
	 * Stores what a change did in the cloud and counts it for [folders], so that a listing of one of them that overlapped
	 * the change is repeated.
	 */
	private inline fun recordChange(vararg folders: VaultPath, storeChange: () -> Unit) {
		synchronized(finishedChanges) {
			storeChange()
			folders.forEach { finishedChanges[it] = (finishedChanges[it] ?: 0) + 1 }
		}
	}

	private fun finishedChangesIn(folder: VaultPath): Long? {
		synchronized(finishedChanges) {
			return finishedChanges[folder]
		}
	}

	private fun write(path: VaultPath, localFile: File, replace: Boolean): CloudFile {
		val size = localFile.length()
		return contentRepository.write(contentRepository.file(parentFolder(path), path.name, size), FileBasedDataSource.from(localFile), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, replace, size)
	}

	fun pendingUploads(vault: Vault): List<VaultPath> {
		return dao.loadPendingUploads(vault.id).map { VaultPath(vault, it.path) }
	}

	private fun requireFolder(folder: VaultPath) {
		require(folder.isRoot || document(folder)?.isFolder == true) { "Not a folder: ${logId(folder)}" }
	}

	private fun unusedPath(folder: VaultPath, name: String): VaultPath {
		var path = folder.resolve(name)
		while (dao.load(path.vault.id, path.path) != null) {
			path = folder.resolve("${path.name}_")
		}
		return path
	}

	private fun unusedConflictPath(path: VaultPath): VaultPath {
		val folder = requireNotNull(path.parent)
		var conflictPath: VaultPath
		do {
			conflictPath = folder.resolve(conflictCopyName(path.name, UUID.randomUUID().toString().take(5).uppercase()))
		} while (dao.load(conflictPath.vault.id, conflictPath.path) != null)
		return conflictPath
	}

	/**
	 * Applies [cloudChange] to [cloudPath]. If the name is taken in the cloud, it is applied to a conflict name instead,
	 * which the node with [id] is renamed to unless it has been moved in the meantime.
	 */
	private fun <T> retryingNameCollisions(id: Long, cloudPath: VaultPath, cloudChange: (VaultPath) -> T): T {
		var current = cloudPath
		repeat(MAX_NAME_COLLISION_RETRIES) {
			try {
				return cloudChange(current)
			} catch (e: CloudNodeAlreadyExistsException) {
				val renamed = unusedConflictPath(current)
				LOG.i("The name of ${documentId(current.vault.id, id)} is taken in the cloud, renaming it")
				dao.loadById(id)?.takeIf { it.path == current.path }?.let { pending ->
					dao.renamePending(current.vault.id, current.path, renamed.path, renamed.name, mimeType(pending.isFolder, renamed.name))
					onChanged(requireNotNull(current.parent))
				}
				current = renamed
			}
		}
		return cloudChange(current)
	}

	private fun applyInBackground(vault: Vault, id: Long, cloudChange: CompletableFuture<*>) {
		cloudChange.whenComplete { _, e ->
			if (e != null) {
				LOG.e(e.cause ?: e, "Applying a change of ${documentId(vault.id, id)} to the cloud failed")
				dao.loadById(id)?.let { discardPendingChange(vault, it) }
			}
		}
	}

	/**
	 * A moved node goes back to where it is in the cloud, unless it was never created there.
	 */
	private fun discardPendingChange(vault: Vault, pending: DocumentMetadataEntity) {
		val sourcePath = pending.sourcePath
		if (sourcePath != null && !pending.isCreating) {
			dao.revertMove(requireNotNull(pending.id), mimeType(pending.isFolder, sourcePath.substringAfterLast('/')))
		} else {
			dao.discardPendingChange(pending)
		}
		onChanged(VaultPath(vault, pending.parentPath))
		sourcePath?.let { onChanged(requireNotNull(VaultPath(vault, it).parent)) }
	}

	private fun lastEnumeratedAt(folder: VaultPath): Long? {
		return dao.load(folder.vault.id, folder.path)?.lastEnumeratedAt
	}

	private fun isListingDue(lastEnumeratedAt: Long?): Boolean {
		return lastEnumeratedAt == null || isOutdated(lastEnumeratedAt)
	}

	private fun isOutdated(timestamp: Long): Boolean {
		return currentTimeMillis() - timestamp >= REFRESH_INTERVAL_MILLIS
	}

	private fun loadVisible(path: VaultPath): DocumentMetadataEntity? {
		return dao.load(path.vault.id, path.path)?.takeUnless { it.isDeleting }
	}

	/**
	 * Unless [force] is set, a folder whose listing failed is not listed again within [REFRESH_INTERVAL_MILLIS].
	 */
	private fun refreshInBackground(folder: VaultPath, force: Boolean) {
		val failedAt = failedListings[folder]?.failedAt
		if ((!force && failedAt != null && !isOutdated(failedAt)) || !refreshing.add(folder)) {
			return
		}
		scheduler.read(folder) { enumerateNow(folder) }.whenComplete { _, e ->
			if (e != null) {
				LOG.w(e.cause ?: e, "Refreshing ${logId(folder)} failed")
				failedListings[folder] = FailedListing(currentTimeMillis(), e.cause ?: e)
			}
			refreshing.remove(folder)
			onChanged(folder)
		}
	}

	private fun enumerate(folder: VaultPath) {
		scheduler.read(folder) { enumerateNow(folder) }.await()
	}

	/**
	 * Gives up after [MAX_LISTING_ATTEMPTS] listings that each overlapped a change to [folder], e.g. while many files are
	 * uploaded into it. The failure is then handled like that of any other listing.
	 */
	private fun enumerateNow(folder: VaultPath) {
		repeat(MAX_LISTING_ATTEMPTS) {
			val finishedChangesBefore = finishedChangesIn(folder)
			val entries = contentRepository.list(safeResolve(folder))
			synchronized(finishedChanges) {
				if (finishedChanges[folder] == finishedChangesBefore) {
					dao.storeChildren(folderEntity(folder, currentTimeMillis()), entries.map { entity(folder.vault, it) })
					failedListings.remove(folder)
					return
				}
			}
			LOG.d("A change reached the cloud while listing ${logId(folder)}, listing it again")
		}
		throw FolderKeptChangingException(logId(folder))
	}

	private fun folderEntity(folder: VaultPath, lastEnumeratedAt: Long): DocumentMetadataEntity {
		return DocumentMetadataEntity().apply {
			vaultId = folder.vault.id
			path = folder.path
			parentPath = folder.parent?.path
			name = folder.name
			mimeType = Document.MIME_TYPE_DIR
			this.lastEnumeratedAt = lastEnumeratedAt
		}
	}

	private fun entity(vault: Vault, node: CloudNode, nodePath: VaultPath = VaultPath(vault, node.path)): DocumentMetadataEntity {
		require(requireNotNull(node.cloud?.type()) == CloudType.CRYPTO)

		return DocumentMetadataEntity().apply {
			vaultId = vault.id
			path = nodePath.path
			parentPath = requireNotNull(nodePath.parent).path
			name = nodePath.name
			when (node) {
				is CryptoFile -> {
					mimeType = fileMimeType(node.name)
					size = node.size
					lastModified = node.modified?.time
				}
				is CryptoSymlink -> {
					mimeType = fileMimeType(node.name)
					lastModified = node.modified?.time
				}
				is CryptoFolder -> mimeType = Document.MIME_TYPE_DIR
				else -> throw IllegalArgumentException("Node needs to be a CryptoNode, was ${node.javaClass.name}")
			}
		}
	}
}

private fun mimeType(isFolder: Boolean, name: String): String {
	return if (isFolder) Document.MIME_TYPE_DIR else fileMimeType(name)
}

private fun fileMimeType(name: String): String {
	return (mimeTypes.fromFilename(name) ?: MimeType.APPLICATION_OCTET_STREAM).toString()
}
