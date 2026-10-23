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
import java.util.concurrent.atomic.AtomicInteger
import timber.log.Timber

private val LOG = Timber.named("DocumentContentCache")

/**
 * Decrypted local copies of the files in vaults. A copy is used as long as the file's modification date in the cloud,
 * compared to the second, is the one the copy is based on, or while the copy has a pending upload. Otherwise the file is
 * downloaded again. Files are written to their local copy, which is uploaded afterwards.
 *
 * Copies are kept, also while the vaults are locked, until their file is deleted or the cache is cleared with [evictAll],
 * as on iOS. Copies with a pending upload survive even that. That is why [directory] must not be in the cache directory,
 * which the system clears when storage runs low. A copy returned by [file] or [fileForWriting] is kept
 * until it is [released][release], so that it can't be deleted before it is opened or while it is written.
 *
 * A copy's cache entry points to the latest version of its file, written or downloaded. Whatever finishes an earlier
 * write, e.g. its upload, a writer closing it or canceling it, leaves a later copy alone, apart from moving its base on,
 * see [upload]. A writer's own copy, see [ownCopy], takes the entry over when its writer closes it. Only a writer whose
 * copy was replaced while it had it open, and whose replacement isn't waiting for its upload, takes the entry back, see
 * [keepWrite].
 */
class DocumentContentCache @JvmOverloads constructor(
	private val dao: CachedFileDao,
	private val metadataCache: DocumentMetadataCache,
	private val scheduler: CloudTaskScheduler,
	private val contentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
	private val directory: File,
	private val thumbnails: ThumbnailStore? = null,
	private val watchClose: (file: File, onClosed: () -> Unit) -> AutoCloseable = ::watchCloseWrite
) {

	private val resumedVaults: MutableSet<Long> = ConcurrentHashMap.newKeySet()

	/**
	 * The downloads of copies that someone waits for, e.g. to open a file. Thumbnails give way to them, see
	 * [ThumbnailGenerator].
	 */
	private val awaitedDownloads = AtomicInteger()

	/**
	 * Called whenever the last download someone waits for has ended.
	 */
	@Volatile
	var onAwaitedDownloadsDone: (() -> Unit)? = null

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

	/**
	 * [replaces] is the local name of the recorded copy that a copy of the writers' own was made from, and
	 * [followsReplacedBase] whether the copy takes that copy's base once it is closed, see [ownCopy].
	 */
	private class Writers(val documentId: Long?, var base: Long?, val replaces: String?, val followsReplacedBase: Boolean) {

		var open = 0

		/**
		 * Whether one of the writers saved the copy while others still had it open, and that save isn't in the cloud yet.
		 * A writer that fails then keeps the copy, see [discard].
		 */
		var holdsSharedSave = false
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
		metadataCache.isOpenForWriting = { id -> synchronized(inUse) { writers.values.any { it.documentId == id && it.open > 0 } } }
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
	 * Downloads [path] into [target] without recording it as a copy. Nobody counts as waiting for it, see
	 * [hasAwaitedDownloads].
	 */
	fun downloadUnrecorded(path: VaultPath, target: File, progressAware: ProgressAware<DownloadState> = ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD) {
		scheduler.download(path) { read(path, target, progressAware) }.await()
	}

	fun hasAwaitedDownloads(): Boolean {
		return awaitedDownloads.get() > 0
	}

	/**
	 * Whether uploads or downloads someone waits for are running. Previews aren't counted, see [downloadUnrecorded].
	 */
	fun hasTransfers(): Boolean {
		return hasAwaitedDownloads() || metadataCache.hasUploads()
	}

	/**
	 * A claimed copy of a file and the version in the cloud it is based on. [replaces] and [followsReplacedBase] are set
	 * for a writer's own copy, see [ownCopy].
	 */
	private class Copy(val file: File, val lastModified: Long?, val replaces: String? = null, val followsReplacedBase: Boolean = false)

	private fun copy(path: VaultPath, signal: CancellationSignal?, progressAware: ProgressAware<DownloadState> = ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD): Copy {
		val metadata = requireFile(metadataCache.requireCurrentDocument(path), path)
		val cached = dao.load(path.vault.id, path.path)
		if (cached != null && (metadata.isUploading || isSameSecond(cached.lastModified, metadata.lastModified))) {
			claimIfPresent(File(directory, cached.localName))?.let { return Copy(it, cached.lastModified) }
		}
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
	 * already exists there. While the recorded copy is shared, the writer gets a copy of its own, see [ownCopy]. A claim
	 * the caller holds on the recorded copy, [claimedByCaller], doesn't count as someone else using it. The copy's
	 * modification date lies at least [CHANGE_DETECTION_MILLIS] in the past, so that any write changes it, also one that
	 * keeps the length right after the copy was filled or downloaded. The caller tells from it whether the writer wrote
	 * anything.
	 */
	fun fileForWriting(path: VaultPath, keepContent: Boolean, signal: CancellationSignal? = null, claimedByCaller: File? = null): File {
		val metadata = requireFile(metadataCache.requireDocument(path), path)
		val pinned = pinnedBase(path)
		val downloaded = if (keepContent && !metadata.isCreating) pinned?.let { pinnedCopy(path, it, signal) } ?: copy(path, signal) else null
		val emptyBase = pinned?.lastModified ?: metadata.lastModified
		// decided together with registering the writer, so that a writer whose file gets a save recorded meanwhile, e.g. an
		// import, gets a copy of its own, too
		val copy = try {
			withCopiesLocked {
				val copy = ownCopy(path, downloaded, claimedByCaller, emptyBase) ?: downloaded ?: emptyCopy(path, emptyBase)
				writers.getOrPut(copy.file.name) { Writers(metadata.id, copy.lastModified, copy.replaces, copy.followsReplacedBase) }.apply {
					base = copy.lastModified
					open++
				}
				copy
			}
		} catch (e: Exception) {
			downloaded?.let { release(it.file) }
			throw e
		}
		if (copy.replaces != null && downloaded != null) {
			try {
				downloaded.file.copyTo(copy.file, overwrite = true)
			} catch (e: Exception) {
				writerClosed(copy.file)
				drop(copy.file)
				throw e
			} finally {
				release(downloaded.file)
			}
		}
		val writtenBefore = System.currentTimeMillis() - CHANGE_DETECTION_MILLIS
		if (copy.file.lastModified() > writtenBefore && !copy.file.setLastModified(writtenBefore)) {
			LOG.w("Failed to date back copy ${copy.file.name}, a write that keeps its length may go unnoticed")
		}
		return copy.file
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
	 * A copy of its own for a writer of [path] while the copy it would get is shared: it holds a save that waits for its
	 * upload, or a reader uses it. That is [downloaded], the claimed copy with the content to keep, also if a later write
	 * overtook it, else the recorded copy, see [emptyCopy]. The writer then changes neither what is uploaded nor what is
	 * read, and a writer that fails leaves that copy as it was. Writers that have the copy open already change it, so
	 * another writer shares it with them, like the writers of a local file. The own copy is created empty; the caller fills
	 * it with the content of [downloaded], see [fileForWriting]. It takes the recorded copy's place once the writer closes
	 * it, unless a later write replaced that meanwhile, see [keepWrite]. It is based on the version of [downloaded], or
	 * [emptyBase] for a write from scratch, unless it follows the replaced copy's base: that of a save waiting for its
	 * upload, which the upload moves on, or an external editor's pinned one. Null if the writer can write to the copy it got.
	 * Unlike on iOS, where the system coordinates the writers of a file, writers here can open a file while an earlier
	 * save of it waits for its upload. Called with the copies locked, see [withCopiesLocked].
	 */
	private fun ownCopy(path: VaultPath, downloaded: Copy?, claimedByCaller: File?, emptyBase: Long?): Copy? {
		val recorded = localCopy(path)
		val shared = downloaded?.file ?: recorded ?: return null
		if (isOpenForWriting(shared)) {
			return null
		}
		val ownClaims = listOfNotNull(downloaded?.file, claimedByCaller).count { it == shared }
		val isRead = (inUse[shared.name] ?: 0) > ownClaims
		val isUploaded = if (shared == recorded) metadataCache.isUploading(path) else metadataCache.hasUploadOf(shared)
		if (!isRead && !isUploaded) {
			return null
		}
		val own = claim(File(directory, UUID.randomUUID().toString()))
		try {
			own.createNewFile()
		} catch (e: Exception) {
			drop(own)
			throw e
		}
		// the recorded copy's base as of now, which its upload may have moved on since it was claimed
		val base = if (downloaded == null) emptyBase else if (shared == recorded) recordedBase(path) else downloaded.lastModified
		return Copy(own, base, replaces = shared.name, followsReplacedBase = isUploaded || pinnedBase(path) != null)
	}

	/**
	 * The version the writes of [path] are based on: the pinned base, else the one of the recorded copy.
	 */
	private fun recordedBase(path: VaultPath): Long? {
		return pinnedBase(path)?.lastModified ?: dao.load(path.vault.id, path.path)?.lastModified
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
	 * copy, e.g. because its app ended, leaves it as it was, also an earlier write in it that waits for its upload. A
	 * writer's own copy that did not take the recorded copy's place is dropped, see [ownCopy].
	 */
	fun closedForWriting(localFile: File, error: IOException?, changed: Boolean, currentPath: () -> VaultPath) {
		if (error is ParcelFileDescriptor.FileDescriptorDetachedException) {
			awaitDetachedWriter(localFile, currentPath)
			return
		}
		val isOwnCopy = synchronized(inUse) { writers[localFile.name]?.replaces != null }
		writerClosed(localFile)
		try {
			if (changed) {
				withCopiesLocked {
					if (error != null) {
						// an own copy that took the recorded copy's place, e.g. while it was detached, may hold other writers'
						// saves by now
						if (!isOwnCopy || dao.loadByLocalName(localFile.name) != null) {
							discard(currentPath(), localFile)
						}
					} else {
						savedWhileShared(localFile)
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
				savedWhileShared(localFile)
				uploadWrite(currentPath(), localFile)
			}
		} catch (e: Exception) {
			LOG.e(e, "Uploading the copy ${localFile.name} written through a detached file descriptor failed")
		} finally {
			release(localFile)
		}
	}

	/**
	 * Marks a save of [localFile], made by a writer that just closed it, if other writers still have it open, see
	 * [Writers.holdsSharedSave]. Called with the copies locked, see [withCopiesLocked].
	 */
	private fun savedWhileShared(localFile: File) {
		if (isOpenForWriting(localFile)) {
			writers[localFile.name]?.holdsSharedSave = true
		}
	}

	/**
	 * Called with the copies locked, see [withCopiesLocked].
	 */
	private fun uploadWrite(path: VaultPath, localFile: File) {
		val kept = keepWrite(path, localFile)
		// a save made while the vault is locked stays pending until the vault's uploads are resumed after the unlock, see
		// resumeUploadsOnce
		if (kept.vault.isUnlocked) {
			upload(kept, localFile)
		}
	}

	/**
	 * Keeps what a writer wrote to [localFile], the copy it got for [path], as the copy of one file, which is marked as
	 * waiting for its upload, and returns where that file is. A copy that is recorded for a file already, e.g. because it
	 * became a conflict copy meanwhile, stays with it. A writer's own copy takes the place of the copy it was made from,
	 * with that copy's base, as long as no other write replaced that one meanwhile, see [ownCopy]. A copy that was
	 * replaced while the writer had it open becomes the copy of [path] again, based on the version it was opened with, so
	 * that its content is kept until it has been uploaded. If the replacement is a write that is still waiting for its
	 * upload, the writer's content becomes a conflict copy next to it instead, so that neither write is lost. Called with
	 * the copies locked, see [withCopiesLocked].
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
		val replaced = writers[localFile.name]?.replaces?.let { File(directory, it) }
		if (replaced != null && isRegisteredCopy(path, replaced)) {
			metadataCache.markPendingUpload(path)
			val writer = writers[localFile.name]
			store(path, localFile, if (writer?.followsReplacedBase == true) recordedBase(path) else writer?.base)
			deleteIfUnrecorded(replaced)
			return path
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

	/**
	 * Gives up a claim on [file]. A copy that isn't recorded, e.g. one a writer's own copy replaced while a reader had it
	 * open, is deleted with its last claim, see [deleteIfUnrecorded].
	 */
	fun release(file: File) {
		synchronized(inUse) {
			releaseClaim(file)
			if (file.name !in inUse) {
				deleteIfUnrecorded(file)
			}
		}
	}

	private fun releaseClaim(file: File) {
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
	 * meanwhile. The next upload of [path] is then a conflict copy, too. A copy that is still open for writing stays
	 * pending after its upload, so that what its writer writes afterwards is kept if the process ends before the writer
	 * closes it. If a later write, e.g. a writer's own copy, replaced [localFile] during the upload and waits for its own
	 * upload, the upload moves that write's base on, unless it went to a conflict copy. A [localFile] that is no longer
	 * recorded is deleted once the upload has ended, see [keepWrite].
	 */
	fun upload(path: VaultPath, localFile: File, progressAware: ProgressAware<UploadState> = ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, modifiedDate: Long? = null, userInitiated: Boolean = false): DocumentMetadataCache.Upload {
		var base: Long? = null
		val baseLastModified = { current: VaultPath -> dao.load(current.vault.id, current.path)?.lastModified.also { base = it } }
		return withCopiesLocked {
			metadataCache.upload(path, localFile, baseLastModified, progressAware, modifiedDate, userInitiated) { current, uploadedPath, lastModified, isLatestWrite ->
				withCopiesLocked {
					val recorded = recordedPath(current, localFile)
					if (recorded == null) {
						// a later write that replaced this copy during the upload, e.g. a writer's own copy, was made on top of it, see
						// keepWrite. Unlike a download recorded meanwhile, it waits for its upload.
						if (uploadedPath == current && metadataCache.isUploading(current) && dao.load(current.vault.id, current.path)?.lastModified == base) {
							recordUploadedBase(current, localFile, lastModified)
						}
						return@withCopiesLocked
					}
					if (uploadedPath == current) {
						recordUploadedBase(recorded, localFile, lastModified)
					} else if (isLatestWrite && !metadataCache.isUploading(recorded) && !isOpenForWriting(localFile) && localCopy(uploadedPath) == null) {
						dao.delete(recorded.vault.id, recorded.path)
						store(uploadedPath, localFile, lastModified)
					}
					// the writers' shared save is in the cloud, as the file or as its conflict copy, unless a writer saved again
					// since the upload's end was recorded, which isLatestWrite doesn't tell
					if (isLatestWrite && !metadataCache.isUploading(recorded)) {
						writers[localFile.name]?.holdsSharedSave = false
					}
					if (isOpenForWriting(localFile) && !metadataCache.isUploading(recorded)) {
						metadataCache.markPendingUpload(recorded)
					}
				}
			}.also { upload ->
				upload.result.whenComplete { _, _ ->
					try {
						// a copy that a writer's own copy replaced while it was uploaded, see keepWrite
						deleteIfUnrecorded(localFile)
					} catch (e: Exception) {
						LOG.e(e, "Deleting the replaced copy ${localFile.name} after its upload failed")
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
				release(staging)
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
	 * Records the version an upload of [uploaded] put at [path] as the base of [path]'s recorded copy.
	 */
	private fun recordUploadedBase(path: VaultPath, uploaded: File, lastModified: Long?) {
		dao.updateLastModified(path.vault.id, path.path, lastModified)
		movePinnedBase(path, uploaded, lastModified)
	}

	/**
	 * Deletes [file] unless it is in use, recorded as a copy, or an upload of it hasn't ended. A recorded one is e.g. an
	 * import that was recorded before it failed, which is then a pending upload that is resumed, or a writer's own copy
	 * that took the recorded copy's place. One that is uploaded is e.g. the copy an own copy replaced, which the end of its
	 * upload deletes, see [upload].
	 */
	private fun deleteIfUnrecorded(file: File) {
		synchronized(inUse) {
			if (dao.loadByLocalName(file.name) == null && !metadataCache.hasUploadOf(file)) {
				deleteUnlessInUse(file)
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
			previous?.let { deleteIfUnrecorded(File(directory, it.localName)) }
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
	 * Stops [upload] and drops what it would have uploaded: a file it added is removed from the cache, a replaced file goes
	 * back to its version in the cloud. What the upload may already have put into the cloud stays, see
	 * [DocumentMetadataCache.abandonUpload]. Nothing is dropped if the upload has ended or the file has been written again
	 * since. A file open for writing is kept, so that the writer's save isn't lost.
	 */
	fun cancel(upload: DocumentMetadataCache.Upload) {
		if (upload.result.isDone) {
			return
		}
		upload.stop()
		// not inline on the caller, e.g. the main thread, if the upload ended meanwhile
		upload.result.whenCompleteAsync({ _, e ->
			if (e != null) {
				try {
					discardCanceled(upload)
				} catch (discardFailure: Exception) {
					LOG.e(discardFailure, "Dropping the canceled upload of ${upload.documentId} failed")
				}
			}
		}, scheduler.unordered)
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
	 * Drops [localFile], the local copy of [path] or wherever its file went since, e.g. a conflict copy, after a failed
	 * write, together with the upload it was waiting for, unless another writer still has it open, it holds another
	 * writer's save that isn't in the cloud yet, see [Writers.holdsSharedSave], or a later write replaced it. A copy kept
	 * for another writer's save is uploaded again with what the failed writer wrote, like a local file that several
	 * programs write. A reader that still has a dropped copy open keeps reading it, but the copy is no longer taken for the
	 * file; it is deleted once the reader releases it, see [release].
	 */
	fun discard(path: VaultPath, localFile: File) {
		withCopiesLocked {
			val recorded = recordedPath(path, localFile)
			if (recorded == null) {
				LOG.w("Not dropping the copy of ${metadataCache.logId(path)} after a failed write, it has been replaced")
			} else if (isOpenForWriting(localFile)) {
				LOG.w("Not dropping the copy of ${metadataCache.logId(recorded)} after a failed write, another writer has it open")
			} else if (writers[localFile.name]?.holdsSharedSave == true) {
				LOG.w("Not dropping the copy of ${metadataCache.logId(recorded)} after a failed write, it holds another writer's save")
				uploadWrite(recorded, localFile)
			} else {
				dao.delete(recorded.vault.id, recorded.path)
				// the failed writer's own claim is given up right after
				if ((inUse[localFile.name] ?: 0) <= 1) {
					deleteCopy(localFile)
				}
				metadataCache.abandonUpload(recorded)
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
	 * Like [resumeUploads], but only the first call per vault after a vault was locked or unlocked does so, see
	 * [forgetResumedUploads]. Files lists a folder again whenever an upload into it fails, which would retry the upload
	 * over and over again.
	 */
	fun resumeUploadsOnce(vault: Vault) {
		if (resumedVaults.add(vault.id)) {
			resumeUploads(vault)
		}
	}

	/**
	 * Lets the next [resumeUploadsOnce] of each vault resume its uploads again. Called whenever a vault was locked or
	 * unlocked, so that the unlock resumes the uploads that failed because the vault was locked.
	 */
	fun forgetResumedUploads() {
		resumedVaults.clear()
	}

	/**
	 * Deletes the files without a cache row, e.g. the copies of removed vaults, and the outdated thumbnails.
	 */
	fun evict() {
		deleteUnrecorded()
		thumbnails?.evict()
	}

	/**
	 * Deletes all thumbnails, and all copies except those that have a pending upload, belong to a file being deleted, are
	 * in use, or an external editor may write.
	 */
	fun evictAll() {
		deleteUnrecorded()
		deleteEvictable()
		thumbnails?.clear()
	}

	fun size(): Long {
		return directory.listFiles()?.sumOf { it.length() } ?: 0L
	}

	/**
	 * Spares a copy that a writer's own copy replaced while its upload hasn't ended, see [keepWrite].
	 */
	private fun deleteUnrecorded() {
		withCopiesLocked {
			val localNames = dao.loadLocalNames().toSet()
			directory.listFiles()?.filter { it.name !in localNames && it.name !in inUse && !metadataCache.hasUploadOf(it) }?.forEach(::deleteCopy)
		}
	}

	/**
	 * Holds off changes while evicting, so that a copy of a file that is about to be deleted is kept for restoring it.
	 */
	private fun deleteEvictable() {
		withCopiesLocked {
			// an external editor may write a pinned copy after the vault locked, see pinBase
			val pinned = pinnedCopies()
			dao.loadEvictable() //
				.filter { it.localName !in inUse && it.localName !in pinned } //
				.forEach { cached ->
					dao.delete(cached.vaultId, cached.path)
					deleteCopy(File(directory, cached.localName))
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
		releaseClaim(file)
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
		awaitedDownloads.incrementAndGet()
		try {
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
		} finally {
			if (awaitedDownloads.decrementAndGet() == 0) {
				onAwaitedDownloadsDone?.invoke()
			}
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

		/**
		 * Well above the modification date granularity of app-internal storage, where the copies are, see [fileForWriting].
		 */
		private const val CHANGE_DETECTION_MILLIS = 1_000L
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
