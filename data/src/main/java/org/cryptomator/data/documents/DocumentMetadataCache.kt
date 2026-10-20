package org.cryptomator.data.documents

import android.provider.DocumentsContract.Document
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.cloud.crypto.CryptoFolder
import org.cryptomator.data.cloud.crypto.CryptoSymlink
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.CloudNodeIsFolderException
import org.cryptomator.domain.exception.MissingCryptorException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.NotFoundException
import org.cryptomator.domain.exception.authentication.AuthenticationException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.repository.CloudRepository
import org.cryptomator.domain.repository.VaultDocumentChangeResult
import org.cryptomator.domain.repository.VaultRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.ThrottlingProgressAware
import org.cryptomator.domain.usecases.cloud.CancelAwareDataSource
import org.cryptomator.domain.usecases.cloud.EmptyDataSource
import org.cryptomator.domain.usecases.cloud.FileBasedDataSource
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypes
import org.cryptomator.util.named
import java.io.File
import java.io.FileNotFoundException
import java.util.Date
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import timber.log.Timber

private val LOG = Timber.named("DocumentMetadataCache")

private val REFRESH_INTERVAL_MILLIS = TimeUnit.SECONDS.toMillis(30)

private const val MAX_NAME_COLLISION_RETRIES = 5

private const val MAX_LISTING_ATTEMPTS = 3

/**
 * How long a new file waits for its first write before it is created empty, see [DocumentMetadataCache.create].
 */
private const val FIRST_WRITE_GRACE_PERIOD = 2_000L
private const val MAX_FIRST_WRITE_WAIT = 60_000L
private const val FIRST_WRITE_POLL_INTERVAL = 50L

/**
 * Answers metadata queries from the persisted cache. The cached children of a folder are returned right away. A folder
 * that has never been listed, or not within [REFRESH_INTERVAL_MILLIS], is listed from the cloud in the background.
 *
 * Changes are applied to the cache first and to the cloud afterwards, ordered by the [scheduler] like in the iOS app.
 * A change is applied to the cache and handed to the scheduler under one lock, so requests are made in the order of
 * the changes. A cloud request uses the path the node had in the cache when the request was made, which is where the
 * node will be in the cloud once the earlier requests are done. Like in the iOS app, the request keeps that path even
 * if one of those earlier requests renamed the node because its name was taken, or was undone. A listing during which
 * a change to the folder reached the cloud is repeated, so that it doesn't overwrite what the change stored.
 *
 * A create, move or delete that the cloud rejects is undone in the cache before the later requests of its paths run.
 * A rejected create or move also marks the affected folders for listing again. A failed upload stays pending instead. A change that fails because its name is taken in the cloud is renamed and
 * retried. [events] are told about every folder whose children a change or a background listing changed.
 *
 * A node being deleted is hidden together with its descendants until the cloud has deleted it. They keep their pending
 * uploads and local copies meanwhile, so that they come back as they were if the cloud rejects the deletion.
 *
 * A move the process ended during is kept if the moved subtree holds a pending upload, since the upload has to go to
 * where the node really is. The uploads in the subtree wait until the listings of the folders at both ends of the move
 * tell where that is. A move of a node whose earlier move hasn't been recorded yet waits for it, so that a move the
 * process ends during always knows where its node was before.
 *
 * Nodes are identified by documentIds made of the vault id and the id of their cache entry, which stays the same when
 * they are renamed or moved. Log messages refer to nodes by their documentId rather than by their cleartext name.
 */
class DocumentMetadataCache @JvmOverloads constructor(
	private val dao: DocumentMetadataDao,
	private val scheduler: CloudTaskScheduler,
	private val events: VaultDocumentEvents,
	private val contentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
	private val cloudRepository: CloudRepository,
	private val vaultRepository: VaultRepository,
	private val mimeTypes: MimeTypes,
	private val deferredMoves: Executor = ForkJoinPool.commonPool(),
	private val firstWriteGracePeriod: Long = FIRST_WRITE_GRACE_PERIOD,
	private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {

	/**
	 * Whether a writer has the file with the given id open, which [create] waits for. Set by the content cache.
	 */
	@Volatile
	var isOpenForWriting: (Long) -> Boolean = { false }

	/**
	 * Held while nodes are added, moved, deleted or marked in the cache, also by the background tasks that store what
	 * the cloud did, so that [withChangesPaused] keeps a node where it was found. Taken before [finishedChanges].
	 */
	private val changeLock = Any()

	/**
	 * How many changes reached the cloud, by the folder whose children they changed; guarded by itself.
	 */
	private val finishedChanges = HashMap<VaultPath, Long>()
	private val refreshing: MutableSet<VaultPath> = ConcurrentHashMap.newKeySet()
	private val failedListings = ConcurrentHashMap<VaultPath, FailedListing>()

	/**
	 * The uploads that have been scheduled and have not ended yet, of every write of a file.
	 */
	private val uploads: MutableSet<Upload> = ConcurrentHashMap.newKeySet()

	/**
	 * How many moves of each node, by id, are waiting for the cloud; guarded by [changeLock].
	 */
	private val pendingMoves = HashMap<Long, Int>()

	/**
	 * The latest move of each node, by id, until it has been recorded; guarded by [changeLock].
	 */
	private val latestMoves = HashMap<Long, CompletableFuture<VaultDocumentChangeResult<CloudNode>>>()
	private val deleteTokens = AtomicLong()

	/**
	 * The moves the process ended during whose subtree holds a pending upload, by node id; guarded by [changeLock].
	 */
	private val interruptedMoves = HashMap<Long, InterruptedMove>()

	/**
	 * What the listings of the folders at both ends of an interrupted move told, `null` until a folder has been listed.
	 */
	private class InterruptedMove(val vaultId: Long) {

		var atSource: Boolean? = null
		var atTarget: Boolean? = null
	}

	/**
	 * Why the latest upload of a file failed, by node id, until another upload of the file starts.
	 */
	private val uploadFailures = ConcurrentHashMap<Long, UploadFailure>()

	private class UploadFailure(val generation: Long, val cause: Throwable)

	/**
	 * The uploads that failed or were stopped once they could have written to the cloud, by node id, until an upload of the
	 * file succeeds. The cloud may have taken such an upload anyway, e.g. if only its response got lost, see
	 * [isUnconfirmedUpload]. Kept in memory only.
	 */
	private val unconfirmedUploads = ConcurrentHashMap<Long, UnconfirmedUpload>()

	/**
	 * Uploads of one of [sizes] bytes each to [cloudPath], where the cloud file had been modified at [cloudModified] before
	 * the first of them.
	 */
	private data class UnconfirmedUpload(val cloudPath: VaultPath, val sizes: Set<Long>, val cloudModified: Long?)

	/**
	 * Called with a vault whenever a listing settled an interrupted move in it, outside of any lock.
	 */
	@Volatile
	var onInterruptedMovesResolved: ((Vault) -> Unit)? = null

	init {
		dao.recoverPendingDeletes()
		dao.loadInterruptedChanges().forEach { pending ->
			if (pending.sourcePath != null && !pending.isCreating && dao.hasPendingUploadInSubtree(pending.vaultId, pending.path)) {
				interruptedMoves[requireNotNull(pending.id)] = InterruptedMove(pending.vaultId)
			} else {
				dao.discardPendingChange(pending)
			}
		}
	}

	/**
	 * [error] tells why a folder without a valid listing, e.g. one that has never been listed, could not be listed.
	 * [authError] tells that listing the folder needs an authentication, also if the cache has an earlier listing of it.
	 */
	class Children(val documents: List<DocumentMetadataEntity>, val isLoading: Boolean, val error: Throwable?, val authError: Throwable? = null)

	class Creation(val documentId: String, val result: CompletableFuture<VaultDocumentChangeResult<CloudNode>>)

	/**
	 * [descendantIds] are the documentIds of the descendants that are deleted along with the node.
	 */
	class Deletion(val descendantIds: List<String>, val result: CompletableFuture<Unit>)

	/**
	 * The cache entry of a file that [createFile] made ready for writing its content; [isNew] unless it replaces a file.
	 */
	class FileTarget(val id: Long, val isNew: Boolean)

	private class FailedListing(val failedAt: Long, val cause: Throwable)

	private class FolderKeptChangingException(folderId: String) : BackendException("$folderId kept changing while it was listed")

	/**
	 * Resolves a documentId: `<vaultId>` for the root of a vault, `<vaultId>:<id>` for a node in it. A node being deleted
	 * is only found if [includeHidden] is set, so that a writer that had it open can still record its changes.
	 */
	fun path(documentId: String, includeHidden: Boolean = false): VaultPath {
		val vaultId = documentId.substringBefore(':').toLongOrNull() ?: throw IllegalArgumentException("Illegal documentId: $documentId")
		val vault = vaultRepository.vaults().find { it.id == vaultId } ?: throw FileNotFoundException("Unknown vault: $vaultId")
		val id = documentId.substringAfter(':', "").ifEmpty { return VaultPath(vault) }
		val metadata = id.toLongOrNull()?.let { if (includeHidden) dao.loadById(it) else dao.loadVisibleById(it) }?.takeIf { it.vaultId == vaultId } ?: throw FileNotFoundException("Unknown document: $documentId")
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
		val failure = failedListings[folder]?.cause?.takeUnless { isLoading }
		val error = failure?.takeIf { lastEnumeratedAt == null }
		val authError = failure?.takeIf { it is AuthenticationException }
		return Children(dao.loadChildren(folder.vault.id, folder.path), isLoading, error, authError)
	}

	/**
	 * Lists [folder] from the cloud now and fails if that fails.
	 */
	fun listNow(folder: VaultPath) {
		ensureFolder(folder)
		enumerate(folder)
	}

	/**
	 * The node at [path] unless it is being deleted; unlike [document], it never lists anything.
	 */
	fun cachedDocument(path: VaultPath): DocumentMetadataEntity? {
		return loadVisible(path)
	}

	fun document(path: VaultPath): DocumentMetadataEntity? {
		if (dao.load(path.vault.id, path.path) != null) {
			return loadVisible(path)
		}
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
	 * Like [document], but also finds a node the app knows about while the cache doesn't, because the cache hasn't
	 * listed its folder or one of its ancestors yet, or not since the node was created.
	 */
	fun requireKnownDocument(path: VaultPath): DocumentMetadataEntity {
		val parent = requireNotNull(path.parent)
		ensureFolder(parent)
		return loadVisible(path) ?: run {
			enumerate(parent)
			loadVisible(path)
		} ?: throw NoSuchCloudFileException(logId(path))
	}

	/**
	 * Makes the cache know [folder] by listing the folders above it, from the deepest one the cache knows downwards, so
	 * that changes can be made in folders that have only been browsed in the cloud.
	 */
	fun ensureFolder(folder: VaultPath) {
		if (folder.isRoot || loadVisible(folder) != null) {
			return
		}
		val parent = requireNotNull(folder.parent)
		ensureFolder(parent)
		enumerate(parent)
		if (loadVisible(folder)?.isFolder != true) {
			throw NoSuchCloudFileException(logId(folder))
		}
	}

	/**
	 * Makes sure the cache knows [folder] and lists it if that has not happened recently, so that a name taken in the
	 * cloud is noticed. If listing fails, the cached children are used, unless [requireListing] is set.
	 */
	fun prepareFolder(folder: VaultPath, requireListing: Boolean) {
		ensureFolder(folder)
		listIfDue(folder, requireListing)
	}

	/**
	 * Like [document], but lists the parent folder from the cloud first if that has not happened recently. If listing
	 * fails, e.g. because there is no connection, the cached metadata is used.
	 */
	fun currentDocument(path: VaultPath): DocumentMetadataEntity? {
		listIfDue(requireNotNull(path.parent), requireListing = false)
		return loadVisible(path)
	}

	private fun listIfDue(folder: VaultPath, requireListing: Boolean) {
		if (!isListingDue(lastEnumeratedAt(folder))) {
			return
		}
		try {
			enumerate(folder)
		} catch (e: Exception) {
			if (requireListing) {
				throw e
			}
			LOG.w(e, "Listing ${logId(folder)} failed, using cached metadata")
		}
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
	 * Unless [failIfTaken] is set, a name that is taken in the cache is changed to an unused one. A new file is only
	 * created empty in the cloud if it isn't written right away, see [awaitFirstWrite].
	 */
	fun create(folder: VaultPath, name: String, isFolder: Boolean, failIfTaken: Boolean = false, userInitiated: Boolean = false): Creation {
		if (failIfTaken) {
			prepareFolder(folder, requireListing = false)
		}
		synchronized(changeLock) {
			requireFolder(folder)
			val path = if (failIfTaken) freePath(folder.resolve(name), isFolder) else unusedPath(folder, name)
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
			val result = applyInBackground(path.vault, id, listOf(path), userInitiated) {
				if (!isFolder && awaitFirstWrite(id)) {
					throw CancellationException("${documentId(path.vault.id, id)} was written before it was created, the upload of the write creates it")
				}
				retryingNameCollisions(id, path) { current ->
					if (isFolder) {
						val createdFolder = contentRepository.create(contentRepository.folder(parentFolder(current), current.name))
						recordChange(folder) { dao.markCreated(id, null, null) }
						VaultDocumentChangeResult<CloudNode>(current, createdFolder)
					} else {
						val createdFile = contentRepository.write(contentRepository.file(parentFolder(current), current.name, 0), EmptyDataSource, ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 0)
						recordChange(folder) { dao.markCreated(id, createdFile.size, createdFile.modified?.time) }
						VaultDocumentChangeResult<CloudNode>(current, createdFile)
					}
				}
			}
			return Creation(documentId(path.vault.id, id), result)
		}
	}

	/**
	 * Adds a file at [path] whose content is written to its local copy and uploaded afterwards. A file already at
	 * [path] is kept if [replace] is set, so that its content can be replaced.
	 */
	fun createFile(path: VaultPath, replace: Boolean): FileTarget {
		synchronized(changeLock) {
			val folder = requireNotNull(path.parent)
			requireFolder(folder)
			loadVisible(path)?.takeIf { replace && !it.isFolder }?.let { return FileTarget(requireNotNull(it.id), isNew = false) }
			freePath(path, isFolder = false)
			val id = insertUploadingFile(path)
			onChanged(folder)
			return FileTarget(id, isNew = true)
		}
	}

	/**
	 * The node with [id] unless it is gone or being deleted; unlike [document], it never lists anything.
	 */
	fun visibleDocument(id: Long): DocumentMetadataEntity? {
		return dao.loadVisibleById(id)
	}

	/**
	 * Unless [failIfTaken] is set, a name that is taken in the cache is changed to an unused one. A file that has never
	 * been in the cloud, e.g. because its upload failed, is only moved in the cache. Its upload then goes to where it is
	 * in the cache.
	 */
	fun move(source: VaultPath, folder: VaultPath, name: String, failIfTaken: Boolean = false, userInitiated: Boolean = false): CompletableFuture<VaultDocumentChangeResult<CloudNode>> {
		return move(source, folder, name, failIfTaken, userInitiated, isQueued = false)
	}

	/**
	 * A move [isQueued] behind an earlier move of the node, see [moveAfter], runs right away, since the moves queued
	 * after it wait for it.
	 */
	private fun move(source: VaultPath, folder: VaultPath, name: String, failIfTaken: Boolean, userInitiated: Boolean, isQueued: Boolean): CompletableFuture<VaultDocumentChangeResult<CloudNode>> {
		if (failIfTaken) {
			prepareFolder(folder, requireListing = false)
		}
		synchronized(changeLock) {
			val metadata = requireVisible(source)
			val earlierMove = latestMoves[requireNotNull(metadata.id)]
			if (!isQueued && earlierMove != null && !earlierMove.isDone) {
				return moveAfter(earlierMove, requireNotNull(metadata.id), source.vault, folder, name, failIfTaken, userInitiated)
			}
			requireFolder(folder)
			check(!dao.hasPendingDeleteInSubtree(source.vault.id, source.path)) { "${logId(source)} contains a node being deleted" }
			val target = if (failIfTaken) freePath(folder.resolve(name), metadata.isFolder) else unusedPath(folder, name)
			val mimeType = mimeType(metadata.isFolder, target.name)
			dao.move(source.vault.id, source.path, target.path, folder.path, target.name, mimeType)
			val sourceFolder = requireNotNull(source.parent)
			onChanged(sourceFolder)
			if (folder != sourceFolder) {
				onChanged(folder)
			}
			val id = requireNotNull(metadata.id)
			pendingMoves[id] = (pendingMoves[id] ?: 0) + 1
			return applyInBackground(source.vault, id, listOf(source, target), userInitiated) {
				try {
					// a node whose move has been undone meanwhile is no longer marked as moving
					val node = dao.loadById(id)?.takeIf { it.sourcePath != null } ?: throw CancellationException("The move of ${documentId(source.vault.id, id)} has been undone")
					if (node.isNeverInCloud) {
						val file = contentRepository.file(parentFolder(target), target.name)
						recordChange(sourceFolder, folder) { markMoved(id, target) }
						return@applyInBackground VaultDocumentChangeResult<CloudNode>(target, file)
					}
					retryingNameCollisions(id, target, ownMoves = 1) { current ->
						val moved = if (metadata.isFolder) {
							contentRepository.move(contentRepository.folder(parentFolder(source), source.name), contentRepository.folder(parentFolder(current), current.name))
						} else {
							contentRepository.move(contentRepository.file(parentFolder(source), source.name), contentRepository.file(parentFolder(current), current.name))
						}
						recordChange(sourceFolder, folder) { markMoved(id, current) }
						VaultDocumentChangeResult<CloudNode>(current, moved)
					}
				} finally {
					synchronized(changeLock) {
						val moves = requireNotNull(pendingMoves[id])
						if (moves > 1) pendingMoves[id] = moves - 1 else pendingMoves.remove(id)
					}
				}
			}.also { move ->
				if (!isQueued) {
					latestMoves[id] = move
					move.whenComplete { _, _ -> synchronized(changeLock) { latestMoves.remove(id, move) } }
				}
			}
		}
	}

	/**
	 * Moves the node with [id] once [earlierMove] of it has been recorded, wherever that left it. A node that is gone or
	 * being deleted by then isn't moved.
	 */
	private fun moveAfter(earlierMove: CompletableFuture<*>, id: Long, vault: Vault, folder: VaultPath, name: String, failIfTaken: Boolean, userInitiated: Boolean): CompletableFuture<VaultDocumentChangeResult<CloudNode>> {
		val result = CompletableFuture<VaultDocumentChangeResult<CloudNode>>()
		latestMoves[id] = result
		result.whenComplete { _, _ -> synchronized(changeLock) { latestMoves.remove(id, result) } }
		// not on the thread that recorded the earlier move, which may be needed to list the target folder
		earlierMove.whenCompleteAsync({ _, _ ->
			try {
				val current = dao.loadVisibleById(id)?.let { VaultPath(vault, it.path) }
				if (current == null) {
					// like a move that has been undone, it's no failure the user has to learn about
					val reason = "${documentId(vault.id, id)} is gone or being deleted, not moving it"
					LOG.i(reason)
					result.completeExceptionally(CancellationException(reason))
					return@whenCompleteAsync
				}
				move(current, folder, name, failIfTaken, userInitiated, isQueued = true).whenComplete { moved, e -> if (e != null) result.completeExceptionally(e) else result.complete(moved) }
			} catch (e: Exception) {
				LOG.e(e, "Moving ${documentId(vault.id, id)} after its earlier move failed")
				events.onChangeFailed(folder.resolve(name), e, userInitiated)
				result.completeExceptionally(e)
			}
		}, deferredMoves)
		return result
	}

	/**
	 * Hides [path] and its descendants until the cloud has deleted it. Their running uploads are stopped. If the cloud
	 * rejects the deletion, they come back as they were, and [Deletion.result] fails. A node that is already gone from
	 * the cloud counts as deleted, a file that has never been in the cloud is only deleted in the cache.
	 */
	fun delete(path: VaultPath, userInitiated: Boolean = false): Deletion {
		synchronized(changeLock) {
			val metadata = requireVisible(path)
			val descendantIds = dao.loadDescendantIds(path.vault.id, path.path).map { documentId(path.vault.id, it) }
			val token = deleteTokens.incrementAndGet()
			dao.markPendingDelete(path.vault.id, path.path, token)
			stopUploads(path)
			val folder = requireNotNull(path.parent)
			onChanged(folder)
			val id = requireNotNull(metadata.id)
			val result = applyInBackground(path.vault, id, listOf(path), userInitiated, undo = { restoreDeleted(path, id, token) }) {
				// a change the deletion waited for failed and dropped the node
				val node = dao.loadById(id)?.takeIf { it.pendingDelete == token }
				if (node != null) {
					if (!node.isNeverInCloud) {
						deleteInCloud(path, metadata.isFolder, id)
					}
					recordChange(folder) { dao.loadById(id)?.let { dao.deleteSubtree(it.vaultId, it.path) } }
				}
			}
			return Deletion(descendantIds, result)
		}
	}

	private fun deleteInCloud(path: VaultPath, isFolder: Boolean, id: Long) {
		try {
			contentRepository.delete(if (isFolder) contentRepository.folder(parentFolder(path), path.name) else contentRepository.file(parentFolder(path), path.name))
		} catch (e: NoSuchCloudFileException) {
			LOG.i("${documentId(path.vault.id, id)} was already gone from the cloud")
		} catch (e: NotFoundException) {
			LOG.i("${documentId(path.vault.id, id)} was already gone from the cloud")
		}
	}

	/**
	 * A node that gave its name to a new node while it was being deleted keeps the conflict name it got, and its folder
	 * is listed again to learn which node is where in the cloud. It is no longer marked as moving away from its name.
	 */
	private fun restoreDeleted(path: VaultPath, id: Long, token: Long) {
		dao.clearPendingDelete(id, token)
		val restored = dao.loadById(id) ?: return
		if (restored.path != path.path) {
			dao.clearSourcePath(id)
			dao.updateLastEnumeratedAt(restored.vaultId, restored.parentPath, null)
		}
		onChanged(VaultPath(path.vault, restored.parentPath))
	}

	/**
	 * Uploads the local copy of a file that has been written. If the upload fails, the file stays marked as uploading.
	 * That keeps the local copy and lists it in [pendingUploads] for a retry. If the file is written again before the
	 * upload starts, only the later write is uploaded. The upload of a file being deleted ends right away; the file stays
	 * marked as uploading, so that its upload is resumed if the cloud rejects the deletion.
	 *
	 * [baseLastModified] tells which version in the cloud the local copy is based on, and defaults to the cached
	 * modification date. If the file changed in the cloud since, the local copy is uploaded as a conflict copy, unless the
	 * change is what an earlier upload of the file wrote although it failed, see [isUnconfirmedUpload]. A file
	 * not based on any version in the cloud is never uploaded over an existing one, but renamed like a new file whose
	 * name is taken.
	 *
	 * [onUploaded] receives where the file is in the cache and where the uploaded content is in the cache, which is the
	 * file itself or its conflict copy. It also tells whether the uploaded copy is still the latest write of the file.
	 * The cloud gets [modifiedDate] as the file's modification date, or that of the local copy if it is null.
	 */
	fun upload(path: VaultPath, localFile: File, baseLastModified: (VaultPath) -> Long?, progressAware: ProgressAware<UploadState> = ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, modifiedDate: Long? = null, userInitiated: Boolean = false, onUploaded: OnUploaded): Upload {
		synchronized(changeLock) {
			dao.markUploading(path.vault.id, path.path)
			val metadata = dao.load(path.vault.id, path.path) ?: throw FileNotFoundException("Unknown document: ${logId(path)}")
			val upload = Upload(path, requireNotNull(metadata.id), metadata.uploadGeneration, localFile, progressAware, modifiedDate, userInitiated, onUploaded)
			uploadFailures.remove(upload.id)
			onChanged(requireNotNull(path.parent))
			if (loadVisible(path) == null) {
				upload.result.completeExceptionally(CancellationException("${logId(path)} is being deleted"))
			} else {
				uploads.add(upload)
				scheduler.upload(path) { uploadNow(upload, baseLastModified) }.whenComplete { _, e ->
					// e.g. the cache could not be read, the upload has not ended then
					if (e != null) {
						uploads.remove(upload)
						upload.result.completeExceptionally(unwrap(e))
					}
				}
			}
			return upload
		}
	}

	fun interface OnUploaded {

		fun onUploaded(path: VaultPath, uploadedPath: VaultPath, lastModified: Long?, isLatestWrite: Boolean)
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
	 * Gives up the pending upload of [path], whose local copy is gone, and marks its folder for listing again. A file that
	 * has never been in the cloud is dropped.
	 */
	fun abandonUpload(path: VaultPath) {
		synchronized(changeLock) {
			val metadata = dao.load(path.vault.id, path.path)?.takeIf { it.isUploading } ?: return
			abandonUpload(metadata)
			onChanged(requireNotNull(path.parent))
		}
	}

	/**
	 * Gives up the pending uploads whose local copy was never recorded, e.g. because the process ended while it was
	 * written.
	 */
	fun abandonUploadsWithoutLocalCopy() {
		synchronized(changeLock) {
			dao.loadUploadsWithoutLocalCopy().forEach(::abandonUpload)
		}
	}

	private fun abandonUpload(metadata: DocumentMetadataEntity) {
		unconfirmedUploads.remove(metadata.id)
		if (metadata.isNeverInCloud) {
			dao.deleteSubtree(metadata.vaultId, metadata.path)
		} else {
			dao.markSynced(requireNotNull(metadata.id))
		}
		dao.updateLastEnumeratedAt(metadata.vaultId, metadata.parentPath, null)
	}

	fun isUploading(path: VaultPath): Boolean {
		return dao.load(path.vault.id, path.path)?.isUploading == true
	}

	/**
	 * Adds a file next to [path] under a conflict name, for a write of [path] that can't take its place, and returns where
	 * it is.
	 */
	fun createConflictCopy(path: VaultPath): VaultPath {
		synchronized(changeLock) {
			val conflictPath = unusedConflictPath(path)
			insertUploadingFile(conflictPath)
			onChanged(requireNotNull(path.parent))
			return conflictPath
		}
	}

	private fun insertUploadingFile(path: VaultPath): Long {
		return dao.insert(DocumentMetadataEntity().apply {
			vaultId = path.vault.id
			this.path = path.path
			parentPath = requireNotNull(path.parent).path
			name = path.name
			mimeType = fileMimeType(path.name)
			status = DocumentMetadataEntity.STATUS_UPLOADING
		})
	}

	/**
	 * Runs [block] while no change is made to the cache, so that a node found by its id stays where it was found and isn't
	 * marked for deletion meanwhile.
	 */
	fun <T> withChangesPaused(block: () -> T): T {
		synchronized(changeLock) {
			return block()
		}
	}

	/**
	 * Whether [path] waits for its upload, or an upload of it hasn't ended, also once its end is recorded but its local
	 * copy isn't updated yet.
	 */
	fun hasUploadNotEnded(path: VaultPath): Boolean {
		val metadata = dao.load(path.vault.id, path.path) ?: return false
		return metadata.isUploading || uploads.any { it.id == metadata.id }
	}

	/**
	 * Whether any upload has been scheduled and hasn't ended yet.
	 */
	fun hasUploads(): Boolean {
		return uploads.isNotEmpty()
	}

	/**
	 * Whether an upload of [localFile] has been scheduled and hasn't ended yet, also one that a later write superseded.
	 */
	fun hasUploadOf(localFile: File): Boolean {
		return uploads.any { it.localFile == localFile }
	}

	fun hasUploadInFlight(path: VaultPath): Boolean {
		val metadata = dao.load(path.vault.id, path.path) ?: return false
		return uploads.any { it.id == metadata.id && it.generation == metadata.uploadGeneration }
	}

	/**
	 * Stops the running uploads of [subtree]. Their local copies stay pending.
	 */
	private fun stopUploads(subtree: VaultPath) {
		uploads.filter { it.vault.id == subtree.vault.id }.forEach { upload ->
			val path = dao.loadById(upload.id)?.let { VaultPath(upload.vault, it.path) }
			if (path == subtree || path?.isAnyChildOf(subtree) == true) {
				upload.stop()
			}
		}
	}

	/**
	 * The upload of one write of a file. [result] ends with a [CancellationException] if the upload was stopped, if the
	 * file has been deleted or is being deleted, or if an interrupted move holds it. If the file is written again before
	 * the upload started, it ends like the upload of that write.
	 */
	inner class Upload internal constructor(
		internal val cloudPath: VaultPath,
		val id: Long,
		val generation: Long,
		val localFile: File,
		internal val progressAware: ProgressAware<UploadState>,
		internal val modifiedDate: Long?,
		internal val userInitiated: Boolean,
		internal val onUploaded: OnUploaded
	) {

		val result = CompletableFuture<VaultDocumentChangeResult<CloudFile>>()

		@Volatile
		internal var isStopped = false
			private set

		val vault: Vault
			get() = cloudPath.vault

		val documentId: String
			get() = documentId(vault.id, id)

		/**
		 * Stops the transfer, but keeps the local copy and its pending upload. Does nothing once the upload has ended.
		 */
		fun stop() {
			if (!result.isDone) {
				isStopped = true
			}
		}

		/**
		 * The upload's node as it is in the cache now, unless the node has been deleted, hidden or written again since.
		 */
		internal fun current(): DocumentMetadataEntity? {
			return dao.loadVisibleById(id)?.takeIf { it.uploadGeneration == generation }
		}
	}

	private fun uploadNow(upload: Upload, baseLastModified: (VaultPath) -> Long?) {
		val metadata = upload.current()
		if (metadata == null || upload.isStopped) {
			uploads.remove(upload)
			endUnperformed(upload)
			return
		}
		val current = VaultPath(upload.vault, metadata.path)
		if (isHeldByInterruptedMove(current)) {
			uploads.remove(upload)
			LOG.i("Uploading ${documentId(metadata)} waits until an interrupted move is settled")
			upload.result.completeExceptionally(CancellationException("Uploading ${documentId(metadata)} waits until an interrupted move is settled"))
			return
		}
		val listenerProgress = ThrottlingProgressAware<UploadState> { events.onUploadProgress(current, it) }
		val progressAware = ProgressAware<UploadState> { progress ->
			upload.progressAware.onProgress(progress)
			listenerProgress.onProgress(progress)
		}
		var attempt: UnconfirmedUpload? = null
		try {
			val cloudPath = upload.cloudPath
			val remoteFile = contentRepository.metadata(contentRepository.file(parentFolder(cloudPath), cloudPath.name))
			val base = baseLastModified(current) ?: metadata.lastModified
			val earlier = unconfirmedUploads[upload.id]?.takeIf { it.cloudPath == cloudPath }
			// also over what an earlier upload of this file wrote, which is no change made elsewhere
			val ownUpload = earlier != null && isUnconfirmedUpload(earlier, remoteFile)
			val replace = remoteFile != null && (ownUpload || base != null && isSameSecond(remoteFile.modified?.time, base))
			val size = upload.localFile.length()
			// the earlier attempts may still land while the cloud is as it was before them, e.g. if the cloud held them
			val stillLanding = earlier?.takeIf { ownUpload || remoteFile?.modified?.time == it.cloudModified }
			val attemptAt = { target: VaultPath ->
				if (target == cloudPath && stillLanding != null) {
					stillLanding.copy(sizes = stillLanding.sizes + size)
				} else {
					// a name the cloud had no file at, see retryingNameCollisions
					UnconfirmedUpload(target, setOf(size), if (target == cloudPath) remoteFile?.modified?.time else null)
				}
			}
			val result = if (replace) {
				attempt = attemptAt(cloudPath)
				uploadTo(upload, cloudPath, replace = true, progressAware)
			} else if (remoteFile == null || base == null) {
				retryingNameCollisions(upload.id, cloudPath) { target ->
					attempt = attemptAt(target)
					uploadTo(upload, target, replace = false, progressAware)
				}
			} else {
				uploadConflictCopy(upload, cloudPath, current, remoteFile, progressAware)
			}
			unconfirmedUploads.remove(upload.id)
			uploads.remove(upload)
			upload.result.complete(result)
		} catch (e: Exception) {
			attempt?.let { unconfirmedUploads[upload.id] = it }
			uploads.remove(upload)
			if (upload.isStopped || e is MissingCryptorException) {
				// an upload that runs after its vault was locked finds no cryptor. It stays pending without counting as failed and
				// is resumed after the next unlock, like one the lock interrupted while encrypting, whose failure that resume clears.
				LOG.i("Uploading ${documentId(metadata)} was stopped")
				upload.result.completeExceptionally(CancellationException("Uploading ${documentId(metadata)} was stopped"))
			} else {
				LOG.e(e, "Uploading ${documentId(metadata)} failed")
				uploadFailures[upload.id] = UploadFailure(upload.generation, e)
				upload.result.completeExceptionally(e)
				events.onChangeFailed(current, e, upload.userInitiated)
			}
		}
		onChanged(requireNotNull(current.parent))
	}

	/**
	 * Why the latest upload of [metadata] failed, unless it has been written or retried since.
	 */
	fun uploadFailure(metadata: DocumentMetadataEntity): Throwable? {
		return uploadFailures[metadata.id]?.takeIf { metadata.isUploading && it.generation == metadata.uploadGeneration }?.cause
	}

	/**
	 * Whether [remoteFile], the cloud file an upload goes to, is what [attempts], unconfirmed earlier uploads of the file to
	 * the same path, wrote: it has the size of one of them and changed since the first of them started. Unlike a change
	 * made elsewhere, it is uploaded over instead of being kept next to a conflict copy, see [unconfirmedUploads]. Another
	 * device's change of the same size made meanwhile is overwritten then, as on iOS, which never checks for changes made
	 * elsewhere before an upload.
	 */
	private fun isUnconfirmedUpload(attempts: UnconfirmedUpload, remoteFile: CloudFile?): Boolean {
		return remoteFile != null && remoteFile.size in attempts.sizes && remoteFile.modified?.time != attempts.cloudModified
	}

	/**
	 * Ends an upload that was not performed, like the upload of the write that superseded it if there is one.
	 */
	private fun endUnperformed(upload: Upload) {
		val superseding = uploads.filter { it.id == upload.id && it.generation > upload.generation }.maxByOrNull { it.generation }
		if (superseding != null && !upload.isStopped) {
			superseding.result.whenComplete { result, e -> if (e != null) upload.result.completeExceptionally(unwrap(e)) else upload.result.complete(result) }
		} else {
			upload.result.completeExceptionally(CancellationException("Uploading ${upload.documentId} was not performed"))
		}
	}

	private fun uploadTo(upload: Upload, cloudPath: VaultPath, replace: Boolean, progressAware: ProgressAware<UploadState>): VaultDocumentChangeResult<CloudFile> {
		val uploadedFile = write(cloudPath, upload, replace, progressAware)
		val uploaded = recordChange(requireNotNull(cloudPath.parent)) {
			dao.loadById(upload.id)?.also { dao.markUploaded(upload.id, uploadedFile.size ?: upload.localFile.length(), uploadedFile.modified?.time, upload.generation) }
		} ?: return VaultDocumentChangeResult(cloudPath, uploadedFile)
		val path = VaultPath(upload.vault, uploaded.path)
		upload.onUploaded.onUploaded(path, path, uploadedFile.modified?.time, uploaded.uploadGeneration == upload.generation)
		return VaultDocumentChangeResult(path, uploadedFile)
	}

	/**
	 * Keeps both versions of a file that changed locally and in the cloud: the local one is uploaded as a conflict copy,
	 * the original takes the cloud version.
	 */
	private fun uploadConflictCopy(upload: Upload, cloudPath: VaultPath, current: VaultPath, remoteFile: CloudFile, progressAware: ProgressAware<UploadState>): VaultDocumentChangeResult<CloudFile> {
		val conflictName = unusedConflictPath(current).name
		LOG.i("${upload.documentId} changed in the cloud, uploading the local changes as a conflict copy")
		val uploadedFile = write(requireNotNull(cloudPath.parent).resolve(conflictName), upload, false, progressAware)
		val uploaded = recordChange(requireNotNull(cloudPath.parent)) {
			dao.loadById(upload.id)?.also { metadata ->
				dao.store(entity(upload.vault, uploadedFile, requireNotNull(VaultPath(upload.vault, metadata.path).parent).resolve(conflictName)))
				dao.markUploaded(upload.id, remoteFile.size, remoteFile.modified?.time, upload.generation)
			}
		} ?: return VaultDocumentChangeResult(requireNotNull(current.parent).resolve(conflictName), uploadedFile)
		val path = VaultPath(upload.vault, uploaded.path)
		val conflictPath = requireNotNull(path.parent).resolve(conflictName)
		upload.onUploaded.onUploaded(path, conflictPath, uploadedFile.modified?.time, uploaded.uploadGeneration == upload.generation)
		return VaultDocumentChangeResult(conflictPath, uploadedFile)
	}

	/**
	 * Stores what a change did in the cloud and counts it for [folders], so that a listing of one of them that overlapped
	 * the change is repeated.
	 */
	private inline fun <T> recordChange(vararg folders: VaultPath, storeChange: () -> T): T {
		synchronized(changeLock) {
			synchronized(finishedChanges) {
				val stored = storeChange()
				folders.forEach { finishedChanges[it] = (finishedChanges[it] ?: 0) + 1 }
				return stored
			}
		}
	}

	private fun finishedChangesIn(folder: VaultPath): Long? {
		synchronized(finishedChanges) {
			return finishedChanges[folder]
		}
	}

	private fun write(path: VaultPath, upload: Upload, replace: Boolean, progressAware: ProgressAware<UploadState>): CloudFile {
		val size = upload.localFile.length()
		val file = FileBasedDataSource.from(upload.localFile)
		val data = CancelAwareDataSource.wrap(upload.modifiedDate?.let { DatedDataSource(file, Date(it)) } ?: file) { upload.isStopped }
		return contentRepository.write(contentRepository.file(parentFolder(path), path.name, size), data, progressAware, replace, size)
	}

	/**
	 * The pending uploads of [vault], except those an interrupted move holds.
	 */
	fun pendingUploads(vault: Vault): List<VaultPath> {
		return dao.loadPendingUploads(vault.id).map { VaultPath(vault, it.path) }.filterNot(::isHeldByInterruptedMove)
	}

	/**
	 * Lists the folders at both ends of the interrupted moves in [vault], so that they can be settled, see
	 * [onInterruptedMovesResolved]. A listing that fails is repeated with the next call.
	 */
	fun resolveInterruptedMoves(vault: Vault) {
		val folders = synchronized(changeLock) {
			interruptedMoves.filter { (_, move) -> move.vaultId == vault.id }.keys.mapNotNull(dao::loadById).flatMap { node ->
				listOfNotNull(VaultPath(vault, node.parentPath), node.sourcePath?.let { VaultPath(vault, it).parent })
			}.distinct()
		}
		folders.forEach { refreshInBackground(it, force = true) }
	}

	private fun isHeldByInterruptedMove(path: VaultPath): Boolean {
		synchronized(changeLock) {
			return interruptedMoves.any { (id, move) ->
				move.vaultId == path.vault.id && dao.loadById(id)?.let { moved -> VaultPath(path.vault, moved.path).let { it == path || path.isAnyChildOf(it) } } == true
			}
		}
	}

	/**
	 * Settles the interrupted moves that have [folder] at one of their ends, from the [entries] a listing of it found:
	 * a node found where it was moved to stays there, one found only where it came from goes back, and one found at
	 * neither stays where it is in the cache. Called with [changeLock] held, before the listing is stored.
	 *
	 * @return whether a move was settled
	 */
	private fun settleInterruptedMoves(folder: VaultPath, entries: List<CloudNode>): Boolean {
		var settled = false
		interruptedMoves.entries.filter { it.value.vaultId == folder.vault.id }.forEach { (id, move) ->
			val node = dao.loadById(id)
			val sourcePath = node?.sourcePath
			if (sourcePath == null) {
				interruptedMoves.remove(id)
				settled = true
				return@forEach
			}
			val source = VaultPath(folder.vault, sourcePath)
			if (source.parent == folder) {
				move.atSource = entries.any { it.name == source.name }
			}
			if (node.parentPath == folder.path) {
				move.atTarget = entries.any { it.name == node.name }
			}
			val atSource = move.atSource ?: return@forEach
			val atTarget = move.atTarget ?: return@forEach
			if (!atTarget && atSource) {
				LOG.i("${documentId(node)} was not moved in the cloud before the process ended, moving it back")
				dao.revertMove(id, mimeType(node.isFolder, source.name))
			} else {
				dao.clearSourcePath(id)
			}
			interruptedMoves.remove(id)
			settled = true
		}
		return settled
	}

	/**
	 * Like [requireDocument], but never lists anything, so that it can be called while changes are made.
	 */
	private fun requireVisible(path: VaultPath): DocumentMetadataEntity {
		return loadVisible(path) ?: throw FileNotFoundException("Unknown document: ${logId(path)}")
	}

	/**
	 * Like [requireVisible] for a folder.
	 */
	private fun requireFolder(folder: VaultPath) {
		require(folder.isRoot || requireVisible(folder).isFolder) { "Not a folder: ${logId(folder)}" }
	}

	/**
	 * [name] in [folder], or if it is taken, the first free name numbered the way Android's own providers do, e.g.
	 * "a (1).txt", which keeps the extension and with it the type.
	 */
	private fun unusedPath(folder: VaultPath, name: String): VaultPath {
		var path = folder.resolve(name)
		var number = 0
		while (dao.load(path.vault.id, path.path) != null) {
			path = folder.resolve(conflictCopyName(name, (++number).toString()))
		}
		return path
	}

	/**
	 * Fails if [target] is taken. A node being deleted gives its name up and takes a conflict name instead, so that it
	 * doesn't share its path with the new node if the deletion fails.
	 */
	private fun freePath(target: VaultPath, isFolder: Boolean): VaultPath {
		val existing = dao.load(target.vault.id, target.path) ?: return target
		if (loadVisible(target) != null) {
			throw if (!isFolder && existing.isFolder) CloudNodeIsFolderException(logId(target)) else CloudNodeAlreadyExistsException(target.name)
		}
		val aside = unusedConflictPath(target)
		LOG.i("${documentId(existing)} is being deleted, giving its name to a new node")
		dao.renamePending(target.vault.id, target.path, aside.path, aside.name, mimeType(existing.isFolder, aside.name), target.path)
		return target
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
	 * Waits for the first write of the new file with [id] and returns whether it came, so that the upload of the write
	 * creates the file instead of an empty file being uploaded first. Unlike on iOS, a file created through the documents
	 * provider gets its content with a later write, e.g. when the Files app copies into a vault. The wait ends after
	 * [firstWriteGracePeriod], or while a writer has the file open once it has closed it, but after [MAX_FIRST_WRITE_WAIT]
	 * at most. It holds the file's path in the [scheduler], so that later changes of the file still find it created.
	 */
	private fun awaitFirstWrite(id: Long): Boolean {
		val start = currentTimeMillis()
		while (true) {
			val metadata = dao.loadById(id) ?: return false
			if (metadata.isUploading) {
				return true
			}
			val waited = currentTimeMillis() - start
			if (waited >= MAX_FIRST_WRITE_WAIT || (waited >= firstWriteGracePeriod && !isOpenForWriting(id))) {
				return false
			}
			Thread.sleep(FIRST_WRITE_POLL_INTERVAL)
		}
	}

	/**
	 * Applies [cloudChange] to [cloudPath]. If the name is taken in the cloud, it is applied to a conflict name instead,
	 * which the node with [id] takes in the cache as well, also if a folder above it has been moved meanwhile. A node
	 * that another change of it has moved away from [cloudPath], e.g. one that gave its name to a new node while being
	 * deleted, records the conflict name as where it is in the cloud instead. [ownMoves] tells how many of the node's
	 * pending moves are [cloudChange] itself.
	 */
	private fun <T> retryingNameCollisions(id: Long, cloudPath: VaultPath, ownMoves: Int = 0, cloudChange: (VaultPath) -> T): T {
		var current = cloudPath
		repeat(MAX_NAME_COLLISION_RETRIES) {
			try {
				return cloudChange(current)
			} catch (e: CloudNodeAlreadyExistsException) {
				LOG.i("The name of ${documentId(current.vault.id, id)} is taken in the cloud, renaming it")
				current = synchronized(changeLock) {
					val renamed = unusedConflictPath(current)
					dao.loadById(id)?.let { pending ->
						val cacheFolder = requireNotNull(VaultPath(current.vault, pending.path).parent)
						val renamedInCache = cacheFolder.resolve(renamed.name)
						val atCurrent = pending.path == current.path || (pending.name == current.name && (pendingMoves[id] ?: 0) == ownMoves)
						if (atCurrent && dao.load(current.vault.id, renamedInCache.path) == null) {
							dao.renamePending(current.vault.id, pending.path, renamedInCache.path, renamedInCache.name, mimeType(pending.isFolder, renamedInCache.name), null)
							onChanged(cacheFolder)
						} else if (pending.sourcePath == current.path) {
							dao.setSourcePath(id, renamed.path)
						}
					}
					renamed
				}
			}
		}
		return cloudChange(current)
	}

	/**
	 * Applies [cloudChange] to the cloud once the earlier changes of [paths] are done. If the cloud rejects it, the change
	 * is undone in the cache with [undo] before the later changes of [paths] run, so that they find it undone. A change
	 * that gives up with a [CancellationException], e.g. because an earlier change undid it, is left as it is.
	 *
	 * @return the outcome of [cloudChange], ending once the change has been undone if the cloud rejected it
	 */
	private fun <T> applyInBackground(vault: Vault, id: Long, paths: List<VaultPath>, userInitiated: Boolean, undo: () -> Unit = { dao.loadById(id)?.let { discardPendingChange(vault, it) } }, cloudChange: () -> T): CompletableFuture<T> {
		val result = CompletableFuture<T>()
		scheduler.write(paths) {
			try {
				cloudChange()
			} catch (e: CancellationException) {
				LOG.i("The change of ${documentId(vault.id, id)} was given up: ${e.message}")
				throw e
			} catch (e: Exception) {
				LOG.e(e, "Applying a change of ${documentId(vault.id, id)} to the cloud failed")
				try {
					synchronized(changeLock) {
						undo()
					}
				} catch (undoFailure: Exception) {
					LOG.e(undoFailure, "Undoing the change of ${documentId(vault.id, id)} failed")
				}
				events.onChangeFailed(paths.last(), e, userInitiated)
				throw e
			}
		}.whenComplete { value, e ->
			if (e == null) {
				result.complete(value)
			} else {
				result.completeExceptionally(unwrap(e))
			}
		}
		return result
	}

	/**
	 * Records that a move of the node with [id] got it to [cloudPath] in the cloud. The node stays marked as moving away
	 * from there until its last pending move is done, also if a folder above it has been moved in the cache meanwhile.
	 * Called with [changeLock] held.
	 */
	private fun markMoved(id: Long, cloudPath: VaultPath) {
		if (pendingMoves[id] == 1) {
			dao.clearSourcePath(id)
		} else {
			dao.setSourcePath(id, cloudPath.path)
		}
	}

	/**
	 * A moved node goes back to where its moves started, see [DocumentMetadataDao.revertMove]. A node still being created
	 * is dropped instead.
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
		return dao.loadVisible(path.vault.id, path.path)
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
			val cause = e?.let(::unwrap)
			refreshing.remove(folder)
			if (cause is MissingCryptorException) {
				// the vault was locked before the listing ran; not a failed listing, so that the folder is listed right after the next
				// unlock. Not announced either: a listing after the announcement would refresh the folder again, over and over
				LOG.i("Refreshing ${logId(folder)} was skipped, the vault has been locked")
				return@whenComplete
			}
			if (cause != null) {
				LOG.w(cause, "Refreshing ${logId(folder)} failed")
				failedListings[folder] = FailedListing(currentTimeMillis(), cause)
			}
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
			val entries = contentRepository.list(cloudFolder(folder))
			val settledMoves = synchronized(changeLock) {
				synchronized(finishedChanges) {
					if (finishedChanges[folder] == finishedChangesBefore) {
						val settled = settleInterruptedMoves(folder, entries)
						dao.storeChildren(folderEntity(folder, currentTimeMillis()), entries.map { entity(folder.vault, it) })
						failedListings.remove(folder)
						settled
					} else {
						null
					}
				}
			}
			if (settledMoves != null) {
				if (settledMoves) {
					onInterruptedMovesResolved?.invoke(folder.vault)
				}
				return
			}
			LOG.d("A change reached the cloud while listing ${logId(folder)}, listing it again")
		}
		throw FolderKeptChangingException(logId(folder))
	}

	private fun onChanged(folder: VaultPath) {
		events.onChanged(folder)
	}

	internal fun cloudFolder(folder: VaultPath): CloudFolder {
		val cloud = cloudRepository.decryptedViewOf(folder.vault)
		//resolve() can't return the root: "/" resolves to a child with an empty name
		return if (folder.isRoot) contentRepository.root(cloud) else contentRepository.resolve(cloud, "/${folder.path}")
	}

	internal fun parentFolder(path: VaultPath): CloudFolder {
		return cloudFolder(requireNotNull(path.parent))
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

	private fun mimeType(isFolder: Boolean, name: String): String {
		return if (isFolder) Document.MIME_TYPE_DIR else fileMimeType(name)
	}

	private fun fileMimeType(name: String): String {
		return (mimeTypes.fromFilename(name) ?: MimeType.APPLICATION_OCTET_STREAM).toString()
	}
}
