package org.cryptomator.data.documents

import android.os.CancellationSignal
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.entities.CachedFileEntity
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.named
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import timber.log.Timber

private val LOG = Timber.named("DocumentContentCache")

internal val MAX_COPY_AGE_MILLIS = TimeUnit.HOURS.toMillis(5)

private const val MAX_SIZE_BYTES = 500L * 1024 * 1024

/**
 * Decrypted local copies of the files in vaults. A copy is used as long as the file's modification date in the cloud,
 * compared to the second, is the one the copy is based on, or while the copy has a pending upload. Otherwise the file is
 * downloaded again. Files are written to their local copy, which is uploaded afterwards.
 *
 * Copies that can be downloaded again are deleted once they are older than [MAX_COPY_AGE_MILLIS] or don't fit into
 * [MAX_SIZE_BYTES] next to newer ones. Copies with a pending upload are kept. That is why [directory] must not be in the
 * cache directory, which the system clears when storage runs low. A copy returned by [file] or [fileForWriting] is kept
 * until it is [released][release], so that it can't be deleted before it is opened or while it is written.
 *
 * A copy's cache entry points to the latest version of its file, written or downloaded. Whatever finishes an earlier
 * write, e.g. its upload, a writer closing it or canceling it, leaves a later copy alone. Only a writer whose copy was
 * replaced while it had it open, and whose replacement isn't waiting for its upload, takes the entry back, see
 * [keepWrite].
 */
class DocumentContentCache @JvmOverloads constructor(
	private val dao: CachedFileDao,
	private val metadataCache: DocumentMetadataCache,
	private val scheduler: CloudTaskScheduler,
	private val contentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
	private val directory: File,
	private val thumbnails: ThumbnailStore? = null,
	private val watchClose: (file: File, onClosed: () -> Unit) -> AutoCloseable = ::watchCloseWrite,
	private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {

	private val resumedVaults: MutableSet<Long> = ConcurrentHashMap.newKeySet()

	/**
	 * How often each copy is in use; guarded by itself, also while copies are deleted, recorded for a file or picked for
	 * an upload. Taken before the metadata cache's change lock, see [withCopiesLocked].
	 */
	private val inUse = HashMap<String, Int>()

	/**
	 * The writers of each copy handed out for writing, by local name; guarded by [inUse]. Kept until the copy's last
	 * claim is released, for the version the copy is based on.
	 */
	private val writers = HashMap<String, Writers>()

	private class Writers(var base: Long?) {

		var open = 0
	}

	/**
	 * The writers that detached the file descriptor of a copy, by local name; guarded by itself.
	 */
	private val detachedWriters = HashMap<String, DetachedWriters>()

	/**
	 * A copy has one watch for all its detached writers, which also keeps it reachable: FileObservers of the same file
	 * share their watch, and a garbage-collected one stops it.
	 */
	private class DetachedWriters {

		var waiting = 0
		var watch: AutoCloseable? = null
	}

	/**
	 * The version in the cloud each document opened for an external editor is based on, by documentId; guarded by
	 * itself. Kept in memory only, so that a document falls back to the usual base after the process ends.
	 */
	private val pinnedBases = HashMap<String, Long?>()

	/**
	 * The copies, by local name, that a writer of a document with a pinned base wrote, or that were uploading when the
	 * base was pinned; guarded by [pinnedBases]. Only their uploads move the base on, not e.g. an import the external
	 * editor never saw.
	 */
	private val pinnedWrites = HashSet<String>()

	init {
		directory.mkdirs()
		metadataCache.abandonUploadsWithoutLocalCopy()
		metadataCache.onInterruptedMovesResolved = { vault -> resumeUploads(vault) }
		evict()
	}

	fun file(path: VaultPath, signal: CancellationSignal? = null, progressAware: ProgressAware<DownloadState> = ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD): File {
		return copy(path, signal, progressAware).file
	}

	/**
	 * Makes the copy of [path] current, so that the app can open it through the documents provider without waiting for
	 * the cloud.
	 */
	fun prepare(path: VaultPath, progressAware: ProgressAware<DownloadState>) {
		release(copy(path, null, progressAware).file)
	}

	/**
	 * Keeps the version the recorded copy of [documentId] is based on as the base of the document's writes until
	 * [unpinBase], also if a reader refreshes the copy meanwhile. An external editor's save then becomes a conflict copy
	 * if the file changed in the cloud since, also if the editor reopens the file to save. An upload of the writers'
	 * content to the document itself moves the base on to the uploaded version, but one that became a conflict copy
	 * doesn't. While pinned, the recorded copy can be written with the vault locked and is kept when copies are evicted.
	 */
	fun pinBase(documentId: String) {
		val path = metadataCache.path(documentId)
		// under the copies lock, an upload's end is recorded either before the pin, which then reads the uploaded version,
		// or after it, and then moves the pin on
		withCopiesLocked {
			val copy = dao.load(path.vault.id, path.path)
			val lastModified = copy?.lastModified ?: metadataCache.cachedDocument(path)?.lastModified
			synchronized(pinnedBases) {
				pinnedBases[documentId] = lastModified
				// the editor opens what is being uploaded, e.g. its own save from before it reopened the file
				if (copy != null && metadataCache.hasUploadNotEnded(path)) {
					pinnedWrites.add(copy.localName)
				}
			}
		}
	}

	fun unpinBase(documentId: String) {
		synchronized(pinnedBases) {
			pinnedBases.remove(documentId)
			if (pinnedBases.isEmpty()) {
				pinnedWrites.clear()
			}
		}
	}

	/**
	 * Whether an external editor's writes of [path] keep a pinned base, see [pinBase].
	 */
	fun isPinned(path: VaultPath): Boolean {
		return pinnedBase(path) != null
	}

	private fun pinnedBase(path: VaultPath): PinnedBase? {
		val documentId = metadataCache.documentId(path) ?: return null
		synchronized(pinnedBases) {
			return if (pinnedBases.containsKey(documentId)) PinnedBase(pinnedBases[documentId]) else null
		}
	}

	private class PinnedBase(val lastModified: Long?)

	private fun movePinnedBase(path: VaultPath, uploaded: File, lastModified: Long?) {
		val documentId = metadataCache.documentId(path) ?: return
		synchronized(pinnedBases) {
			// not consumed, since a copy's overlapping writes may end in one upload or in several
			if (uploaded.name in pinnedWrites && pinnedBases.containsKey(documentId)) {
				pinnedBases[documentId] = lastModified
			}
		}
	}

	/**
	 * The local names of the recorded copies of the documents whose base is pinned.
	 */
	private fun pinnedCopies(): Set<String> {
		val documentIds = synchronized(pinnedBases) { pinnedBases.keys.toList() }
		return documentIds.mapNotNull { documentId ->
			try {
				localCopy(metadataCache.path(documentId))?.name
			} catch (e: FileNotFoundException) {
				null
			}
		}.toSet()
	}

	/**
	 * Claims the recorded copy of a document whose base is pinned, without asking the cloud, e.g. while its vault is
	 * locked. Returns null if no base is pinned or no copy is recorded.
	 */
	fun pinnedFile(path: VaultPath): File? {
		pinnedBase(path) ?: return null
		return localCopy(path)?.let(::claimIfPresent)
	}

	/**
	 * Claims the recorded copy of [metadata]'s file if it is current as far as the cache knows, without asking the cloud.
	 */
	fun currentCopy(path: VaultPath, metadata: DocumentMetadataEntity): File? {
		val cached = dao.load(path.vault.id, path.path) ?: return null
		if (!metadata.isUploading && !isSameSecond(cached.lastModified, metadata.lastModified)) {
			return null
		}
		return claimIfPresent(File(directory, cached.localName))
	}

	/**
	 * Downloads [path] into [target] without recording it as a copy.
	 */
	fun downloadUnrecorded(path: VaultPath, target: File) {
		scheduler.download(path) { read(path, target, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD) }.await()
	}

	/**
	 * A claimed copy of a file and the version in the cloud it is based on.
	 */
	private class Copy(val file: File, val lastModified: Long?)

	private fun copy(path: VaultPath, signal: CancellationSignal?, progressAware: ProgressAware<DownloadState> = ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD): Copy {
		val metadata = requireFile(metadataCache.requireCurrentDocument(path), path)
		val cached = dao.load(path.vault.id, path.path)
		if (cached != null && (metadata.isUploading || isSameSecond(cached.lastModified, metadata.lastModified))) {
			claimIfPresent(File(directory, cached.localName))?.let { return Copy(it, cached.lastModified) }
		}
		evict(MAX_COPY_AGE_MILLIS)
		val outdated = localCopy(path)
		val downloadedFile = claim(File(directory, UUID.randomUUID().toString()))
		val downloaded = download(path, downloadedFile, signal, progressAware) ?: metadata.lastModified
		withCopiesLocked {
			// a write recorded while downloading keeps its copy, also if it went to the cached one; the download is then only handed to the reader
			if (localCopy(path) == outdated && !metadataCache.isUploading(path)) {
				storeOrDrop(path, downloadedFile, downloaded)
				outdated?.let(::deleteUnlessInUse)
			}
		}
		return Copy(downloadedFile, downloaded)
	}

	/**
	 * Returns the local copy to write to. Its content is only fetched from the cloud if [keepContent] is set and the file
	 * already exists there.
	 */
	fun fileForWriting(path: VaultPath, keepContent: Boolean, signal: CancellationSignal? = null): File {
		val metadata = requireFile(metadataCache.requireDocument(path), path)
		val pinned = pinnedBase(path)
		val downloaded = if (keepContent && !metadata.isCreating) pinned?.let { pinnedCopy(path, it, signal) } ?: copy(path, signal) else null
		return withCopiesLocked {
			val copy = downloaded ?: emptyCopy(path, pinned?.lastModified ?: metadata.lastModified)
			writers.getOrPut(copy.file.name) { Writers(copy.lastModified) }.apply {
				base = copy.lastModified
				open++
			}
			copy.file
		}
	}

	/**
	 * The copy an external editor writes to, based on the pinned version, also if the cloud has a newer one by now. Only
	 * a copy that is no longer recorded is downloaded again.
	 */
	private fun pinnedCopy(path: VaultPath, pinned: PinnedBase, signal: CancellationSignal?): Copy {
		val recorded = localCopy(path)?.let(::claimIfPresent) ?: copy(path, signal).file
		return Copy(recorded, pinned.lastModified)
	}

	/**
	 * A copy to write [path] from scratch, based on [lastModified]: the recorded copy if there is one, else a new one. The
	 * recorded copy is kept if recording the new base fails, since it may hold a write that waits for its upload.
	 */
	private fun emptyCopy(path: VaultPath, lastModified: Long?): Copy {
		localCopy(path)?.let(::claimIfPresent)?.let { recorded ->
			try {
				store(path, recorded, lastModified)
			} catch (e: Exception) {
				release(recorded)
				throw e
			}
			return Copy(recorded, lastModified)
		}
		val file = claim(File(directory, UUID.randomUUID().toString())).apply { createNewFile() }
		storeOrDrop(path, file, lastModified)
		return Copy(file, lastModified)
	}

	/**
	 * Handles a copy returned by [fileForWriting] being closed by its writer: a changed copy is uploaded to where
	 * [currentPath] tells the file is now, or dropped if its writer failed. A writer that failed without changing the
	 * copy, e.g. because its app ended, leaves it as it was, also an earlier write in it that waits for its upload.
	 */
	fun closedForWriting(localFile: File, error: IOException?, changed: Boolean, currentPath: () -> VaultPath) {
		if (error is ParcelFileDescriptor.FileDescriptorDetachedException) {
			awaitDetachedWriter(localFile, currentPath)
			return
		}
		writerClosed(localFile)
		try {
			if (changed) {
				withCopiesLocked {
					if (error != null) {
						discard(currentPath(), localFile)
					} else {
						uploadWrite(currentPath(), localFile)
					}
				}
			}
		} finally {
			release(localFile)
		}
	}

	/**
	 * A writer that detached the file descriptor, e.g. to hand it to native code, goes on writing without the provider
	 * noticing. Its copy is kept as a pending write right away, see [keepWrite], so that it is resumed if the close is
	 * missed or the process ends. It is uploaded once the writer closes the file. Each close of the copy is taken for one
	 * of its detached writers, since the watch can't tell them apart.
	 */
	private fun awaitDetachedWriter(localFile: File, currentPath: () -> VaultPath) {
		try {
			withCopiesLocked {
				keepWrite(currentPath(), localFile)
			}
		} catch (e: Exception) {
			writerClosed(localFile)
			release(localFile)
			throw e
		}
		synchronized(detachedWriters) {
			val writers = detachedWriters.getOrPut(localFile.name) { DetachedWriters() }
			if (++writers.waiting == 1) {
				val watch = watchClose(localFile) { detachedWriterClosed(localFile, currentPath) }
				// unless the close was reported right away
				if (writers.waiting == 0) {
					watch.close()
				} else {
					writers.watch = watch
				}
			}
		}
	}

	private fun detachedWriterClosed(localFile: File, currentPath: () -> VaultPath) {
		synchronized(detachedWriters) {
			val writers = detachedWriters[localFile.name] ?: return
			if (--writers.waiting == 0) {
				detachedWriters.remove(localFile.name)
				writers.watch?.close()
			}
		}
		writerClosed(localFile)
		try {
			withCopiesLocked {
				uploadWrite(currentPath(), localFile)
			}
		} catch (e: Exception) {
			LOG.e(e, "Uploading the copy ${localFile.name} written through a detached file descriptor failed")
		} finally {
			release(localFile)
		}
	}

	/**
	 * Called with the copies locked, see [withCopiesLocked].
	 */
	private fun uploadWrite(path: VaultPath, localFile: File) {
		val kept = keepWrite(path, localFile)
		// a save made while the vault is locked stays pending until the unlock resumes the vault's uploads
		if (kept.vault.isUnlocked) {
			upload(kept, localFile)
		}
	}

	/**
	 * Keeps what a writer wrote to [localFile], the copy it got for [path], as the copy of one file, which is marked as
	 * waiting for its upload, and returns where that file is. A copy that is recorded for a file already, e.g. because it
	 * became a conflict copy meanwhile, stays with it. A copy that was replaced while the writer had it open becomes the
	 * copy of [path] again, based on the version it was opened with, so that its content is kept until it has been
	 * uploaded. If the replacement is a write that is still waiting for its upload, the writer's content becomes a
	 * conflict copy next to it instead, so that neither write is lost. Called with the copies locked, see
	 * [withCopiesLocked].
	 */
	private fun keepWrite(path: VaultPath, localFile: File): VaultPath {
		if (isPinned(path)) {
			synchronized(pinnedBases) {
				pinnedWrites.add(localFile.name)
			}
		}
		recordedPath(path, localFile)?.let { recorded ->
			// while locked, no upload follows to mark the save, so it gets a new upload generation here. An earlier upload
			// of the copy that ends meanwhile then leaves the save pending.
			if (!metadataCache.isUploading(recorded) || !recorded.vault.isUnlocked) {
				metadataCache.markPendingUpload(recorded)
				// a reader may have refreshed the copy to a newer version than the pinned base meanwhile
				pinnedBase(recorded)?.let { store(recorded, localFile, it.lastModified) }
			}
			return recorded
		}
		return if (metadataCache.isUploading(path)) {
			LOG.i("A later write of ${metadataCache.logId(path)} is waiting for its upload, keeping this one as a conflict copy")
			metadataCache.createConflictCopy(path).also { store(it, localFile, null) }
		} else {
			// marked first, so that the copy is never taken for the version in the cloud
			metadataCache.markPendingUpload(path)
			path.also { store(it, localFile, writers[localFile.name]?.base) }
		}
	}

	/**
	 * Where [localFile] is recorded as a copy: at [path], or wherever its file went since, e.g. because it was moved or
	 * became a conflict copy.
	 */
	private fun recordedPath(path: VaultPath, localFile: File): VaultPath? {
		return if (isRegisteredCopy(path, localFile)) path else dao.loadByLocalName(localFile.name)?.let { VaultPath(path.vault, it.path) }
	}

	private fun isOpenForWriting(localFile: File): Boolean {
		return (writers[localFile.name]?.open ?: 0) > 0
	}

	private fun writerClosed(localFile: File) {
		synchronized(inUse) {
			writers[localFile.name]?.let { it.open = maxOf(it.open - 1, 0) }
		}
	}

	fun release(file: File) {
		synchronized(inUse) {
			val uses = inUse[file.name] ?: return
			if (uses > 1) {
				inUse[file.name] = uses - 1
			} else {
				inUse.remove(file.name)
				writers.remove(file.name)
			}
		}
	}

	/**
	 * Uploads the local copy of [path]. If the file changed in the cloud since the version the copy is based on, the
	 * copy is uploaded next to it as a conflict copy and becomes the conflict copy's local copy. It stays the copy of
	 * [path] instead if it has been written again or is open for writing, or if the conflict copy has been written
	 * meanwhile. The next upload of [path] is then a conflict copy, too. A copy that is still open for writing stays pending after its upload, so that what its writer writes afterwards is
	 * kept if the process ends before the writer closes it.
	 */
	fun upload(path: VaultPath, localFile: File, progressAware: ProgressAware<UploadState> = ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, modifiedDate: Long? = null, userInitiated: Boolean = false): DocumentMetadataCache.Upload {
		val baseLastModified = { current: VaultPath -> dao.load(current.vault.id, current.path)?.lastModified }
		return withCopiesLocked {
			metadataCache.upload(path, localFile, baseLastModified, progressAware, modifiedDate, userInitiated) { current, uploadedPath, lastModified, isLatestWrite ->
				withCopiesLocked {
					val recorded = recordedPath(current, localFile) ?: return@withCopiesLocked
					if (uploadedPath == current) {
						dao.updateLastModified(recorded.vault.id, recorded.path, lastModified)
						movePinnedBase(recorded, localFile, lastModified)
					} else if (isLatestWrite && !metadataCache.isUploading(recorded) && !isOpenForWriting(localFile) && localCopy(uploadedPath) == null) {
						dao.delete(recorded.vault.id, recorded.path)
						store(uploadedPath, localFile, lastModified)
					}
					if (isOpenForWriting(localFile) && !metadataCache.isUploading(recorded)) {
						metadataCache.markPendingUpload(recorded)
					}
				}
			}
		}
	}

	/**
	 * Adds the file [name] to [folder], or replaces its content if [replace] is set. [write] writes the content to a new
	 * local copy, which is then uploaded with [modifiedDate] as its modification date. A copy that could not be written
	 * completely is dropped, together with the file if it was new; a replaced file keeps its previous copy then. The file
	 * is followed if it is renamed or moved meanwhile.
	 */
	fun importFile(folder: VaultPath, name: String, replace: Boolean, requireListing: Boolean, userInitiated: Boolean = false, progressAware: ProgressAware<UploadState>, modifiedDate: Long?, write: (OutputStream) -> Unit): DocumentMetadataCache.Upload {
		metadataCache.prepareFolder(folder, requireListing)
		val path = folder.resolve(name)
		val target = metadataCache.createFile(path, replace)
		val staging = claim(File(directory, UUID.randomUUID().toString()))
		val upload = try {
			staging.outputStream().use(write)
			commitImport(target, path, staging, progressAware, modifiedDate, userInitiated)
		} catch (e: Exception) {
			try {
				dropUnrecorded(staging)
				if (target.isNew) {
					dropUnwritten(folder.vault, target)
				}
			} catch (cleanupFailure: Exception) {
				e.addSuppressed(cleanupFailure)
			}
			throw e
		}
		release(staging)
		return upload
	}

	/**
	 * Gives up a claim on [staging] and deletes it, unless it was recorded as a copy before the import failed, e.g. when
	 * starting its upload failed. It is then a pending upload, which is resumed.
	 */
	private fun dropUnrecorded(staging: File) {
		synchronized(inUse) {
			release(staging)
			if (dao.loadByLocalName(staging.name) == null) {
				deleteUnlessInUse(staging)
			}
		}
	}

	/**
	 * The file is marked as waiting for its upload before its new copy is recorded, so that the copy is never taken for
	 * the version in the cloud. That holds for a new file, too, since another import may have uploaded it meanwhile.
	 */
	private fun commitImport(target: DocumentMetadataCache.FileTarget, requestedPath: VaultPath, staging: File, progressAware: ProgressAware<UploadState>, modifiedDate: Long?, userInitiated: Boolean): DocumentMetadataCache.Upload {
		return withCopiesLocked {
			val metadata = metadataCache.visibleDocument(target.id)?.takeUnless { it.isFolder } ?: throw NoSuchCloudFileException(metadataCache.logId(requestedPath))
			val path = VaultPath(requestedPath.vault, metadata.path)
			metadataCache.markPendingUpload(path)
			val previous = dao.load(path.vault.id, path.path)
			store(path, staging, if (target.isNew) null else metadata.lastModified)
			previous?.let { deleteUnlessInUse(File(directory, it.localName)) }
			upload(path, staging, progressAware, modifiedDate, userInitiated)
		}
	}

	/**
	 * Drops the file a new import added, wherever it is now, unless someone else wrote it meanwhile.
	 */
	private fun dropUnwritten(vault: Vault, target: DocumentMetadataCache.FileTarget) {
		withCopiesLocked {
			val path = metadataCache.visibleDocument(target.id)?.let { VaultPath(vault, it.path) }
			if (path != null && dao.load(path.vault.id, path.path) == null) {
				metadataCache.abandonUpload(path)
			}
		}
	}

	/**
	 * Stops [upload] and drops what it would have uploaded: a file it added is removed, a replaced file goes back to its
	 * version in the cloud. Nothing is dropped if the upload has ended or the file has been written again since. A file
	 * open for writing is kept, so that the writer's save isn't lost.
	 */
	fun cancel(upload: DocumentMetadataCache.Upload) {
		if (upload.result.isDone) {
			return
		}
		upload.stop()
		upload.result.whenComplete { _, e ->
			if (e != null) {
				try {
					discardCanceled(upload)
				} catch (discardFailure: Exception) {
					LOG.e(discardFailure, "Dropping the canceled upload of ${upload.documentId} failed")
				}
			}
		}
	}

	private fun discardCanceled(upload: DocumentMetadataCache.Upload) {
		withCopiesLocked {
			val path = upload.current()?.let { VaultPath(upload.vault, it.path) }
			if (path == null || !isRegisteredCopy(path, upload.localFile)) {
				return@withCopiesLocked
			}
			if (isOpenForWriting(upload.localFile)) {
				LOG.w("Not dropping the canceled upload of ${metadataCache.logId(path)}, it is open for writing")
				return@withCopiesLocked
			}
			dao.delete(path.vault.id, path.path)
			deleteUnlessInUse(upload.localFile)
			metadataCache.abandonUpload(path)
		}
	}

	/**
	 * Drops [localFile], the local copy of [path], after a failed write, together with the upload it was waiting for,
	 * unless someone other than the failed writer still uses it or a later write replaced it.
	 */
	fun discard(path: VaultPath, localFile: File) {
		withCopiesLocked {
			if (!isRegisteredCopy(path, localFile)) {
				LOG.w("Not dropping the copy of ${metadataCache.logId(path)} after a failed write, it has been replaced")
			} else if ((inUse[localFile.name] ?: 0) > 1) {
				LOG.w("Not dropping the copy of ${metadataCache.logId(path)} after a failed write, it is still in use")
			} else {
				dao.delete(path.vault.id, path.path)
				deleteCopy(localFile)
				metadataCache.abandonUpload(path)
			}
		}
	}

	/**
	 * Hides [path] until the cloud has deleted it, see [DocumentMetadataCache.delete]. If the cloud rejects it, the pending
	 * uploads of the vault are resumed, among them those the deletion stopped.
	 */
	fun delete(path: VaultPath, userInitiated: Boolean = false): DocumentMetadataCache.Deletion {
		val documentId = metadataCache.documentId(path)
		return metadataCache.delete(path, userInitiated).also { deletion ->
			deletion.result.whenComplete { _, e ->
				if (e == null) {
					thumbnails?.delete(listOfNotNull(documentId) + deletion.descendantIds)
				} else {
					try {
						resumeUploads(path.vault)
					} catch (resumeFailure: Exception) {
						LOG.e(resumeFailure, "Resuming the uploads of vault ${path.vault.id} after a rejected deletion failed")
					}
				}
			}
		}
	}

	/**
	 * Retries the uploads of [vault] that did not finish and are not running, or only those of [paths] if given. The
	 * uploads an interrupted move holds are retried once it is settled.
	 */
	fun resumeUploads(vault: Vault, paths: List<VaultPath>? = null, userInitiated: Boolean = false) {
		metadataCache.resolveInterruptedMoves(vault)
		metadataCache.pendingUploads(vault).filter { paths == null || it in paths }.forEach { path ->
			try {
				withCopiesLocked {
					if (metadataCache.isUploading(path) && !metadataCache.hasUploadInFlight(path)) {
						localCopy(path)?.let { upload(path, it, userInitiated = userInitiated) }
					}
				}
			} catch (e: Exception) {
				LOG.e(e, "Resuming the upload of ${metadataCache.logId(path)} failed")
			}
		}
	}

	/**
	 * Like [resumeUploads], but only the first call per vault and process does so. Files lists a folder again whenever
	 * an upload into it fails, which would retry the upload over and over again.
	 */
	fun resumeUploadsOnce(vault: Vault) {
		if (resumedVaults.add(vault.id)) {
			resumeUploads(vault)
		}
	}

	/**
	 * Deletes the files without a cache row, and the copies that can be downloaded again and are too old or too many.
	 * Copies in use or pinned for an external editor are kept. The outdated thumbnails are deleted, too.
	 */
	fun evict() {
		evict(MAX_COPY_AGE_MILLIS)
		thumbnails?.evict()
	}

	/**
	 * Deletes all thumbnails, and all copies except those that have a pending upload, belong to a file being deleted, are
	 * in use, or an external editor may write.
	 */
	fun evictAll() {
		evict(0)
		thumbnails?.clear()
	}

	fun size(): Long {
		return directory.listFiles()?.sumOf { it.length() } ?: 0L
	}

	/**
	 * Holds off changes while evicting, so that a copy of a file that is about to be deleted is kept for restoring it.
	 */
	private fun evict(maxAgeMillis: Long) {
		withCopiesLocked {
			val localNames = dao.loadLocalNames().toSet()
			directory.listFiles()?.filter { it.name !in localNames && it.name !in inUse }?.forEach(::deleteCopy)
			// an external editor may write a pinned copy after the vault locked, see pinBase
			val pinned = pinnedCopies()
			var size = 0L
			dao.loadEvictable() //
				.filter { it.localName !in inUse && it.localName !in pinned } //
				.map { it to File(directory, it.localName) } //
				.sortedByDescending { (_, file) -> file.lastModified() } //
				.forEach { (cached, file) ->
					size += file.length()
					if (currentTimeMillis() - file.lastModified() >= maxAgeMillis || size > MAX_SIZE_BYTES) {
						dao.delete(cached.vaultId, cached.path)
						deleteCopy(file)
					}
				}
		}
	}

	/**
	 * Runs [block] while no copy is claimed or released and no change is made to the cache, so that a file found at a
	 * path stays there until [block] has recorded what it did with its copy.
	 */
	private fun <T> withCopiesLocked(block: () -> T): T {
		synchronized(inUse) {
			return metadataCache.withChangesPaused(block)
		}
	}

	private fun claim(file: File): File {
		synchronized(inUse) {
			inUse[file.name] = (inUse[file.name] ?: 0) + 1
		}
		return file
	}

	private fun claimIfPresent(file: File): File? {
		synchronized(inUse) {
			return if (file.exists()) claim(file) else null
		}
	}

	private fun drop(file: File) {
		release(file)
		deleteUnlessInUse(file)
	}

	private fun deleteUnlessInUse(file: File) {
		synchronized(inUse) {
			if (file.name !in inUse) {
				deleteCopy(file)
			}
		}
	}

	private fun localCopy(path: VaultPath): File? {
		return dao.load(path.vault.id, path.path)?.let { File(directory, it.localName) }
	}

	/**
	 * Whether [localFile] is the copy the cache records for [path], i.e. no later write or download replaced it.
	 */
	private fun isRegisteredCopy(path: VaultPath, localFile: File): Boolean {
		return localCopy(path) == localFile
	}

	private fun requireFile(metadata: DocumentMetadataEntity, path: VaultPath): DocumentMetadataEntity {
		require(!metadata.isFolder) { "Not a file: ${metadataCache.logId(path)}" }
		return metadata
	}

	/**
	 * Records [localFile] as the copy of [path], or gives it up if that fails, e.g. because [path] is gone.
	 */
	private fun storeOrDrop(path: VaultPath, localFile: File, lastModified: Long?) {
		try {
			store(path, localFile, lastModified)
		} catch (e: Exception) {
			drop(localFile)
			throw e
		}
	}

	private fun store(path: VaultPath, localFile: File, lastModified: Long?) {
		dao.store(CachedFileEntity().apply {
			vaultId = path.vault.id
			this.path = path.path
			localName = localFile.name
			this.lastModified = lastModified
		})
	}

	/**
	 * Downloads [path] into the claimed [localFile]. If it fails or is canceled, the claim is given up once the download
	 * has actually stopped.
	 *
	 * @return the modification date of the downloaded version, which can be newer than the one in the cache
	 */
	private fun download(path: VaultPath, localFile: File, signal: CancellationSignal?, progressAware: ProgressAware<DownloadState>): Long? {
		val transfer = scheduler.download(path) {
			signal?.throwIfCanceled()
			read(path, localFile, progressAware)
		}
		try {
			return transfer.await(signal)
		} catch (e: Exception) {
			transfer.whenComplete { _, _ -> drop(localFile) }
			throw e
		}
	}

	/**
	 * @return the modification date of the version that was read
	 */
	private fun read(path: VaultPath, target: File, progressAware: ProgressAware<DownloadState>): Long? {
		val cloudFile = contentRepository.metadata(contentRepository.file(metadataCache.parentFolder(path), path.name)) ?: throw FileNotFoundException("${metadataCache.logId(path)} is gone from the cloud")
		target.outputStream().use { contentRepository.read(cloudFile, it, progressAware) }
		return cloudFile.modified?.time
	}

	private fun deleteCopy(file: File) {
		if (file.exists() && !file.delete()) {
			LOG.w("Failed to delete copy ${file.name}")
		}
	}

	companion object {

		const val DIRECTORY_NAME = "documents-provider"
	}
}

/**
 * Calls [onClosed] when a file descriptor that was opened for writing [file] is closed, in any process.
 */
@Suppress("DEPRECATION") // FileObserver(File, Int) needs API 29
private fun watchCloseWrite(file: File, onClosed: () -> Unit): AutoCloseable {
	val observer = object : FileObserver(file.path, CLOSE_WRITE) {
		override fun onEvent(event: Int, path: String?) {
			// also called with IN_IGNORED once the watch is stopped
			if ((event and CLOSE_WRITE) != 0) {
				onClosed()
			}
		}
	}
	observer.startWatching()
	return AutoCloseable { observer.stopWatching() }
}
