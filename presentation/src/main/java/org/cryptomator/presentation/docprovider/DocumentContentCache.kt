package org.cryptomator.presentation.docprovider

import android.os.CancellationSignal
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.entities.CachedFileEntity
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.Vault
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.util.named
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import timber.log.Timber

private val LOG = Timber.named("DocumentContentCache")

private val MAX_COPY_AGE_MILLIS = TimeUnit.HOURS.toMillis(5)

private const val MAX_SIZE_BYTES = 500L * 1024 * 1024

/**
 * Decrypted local copies of the files opened through the documents provider. A copy is used as long as the file's
 * modification date in the cloud, compared to the second, is the one the copy is based on, or while the copy has a
 * pending upload. Otherwise the file is downloaded again. Files are written to their local copy, which is uploaded
 * afterwards.
 *
 * Copies that can be downloaded again are deleted once they are older than [MAX_COPY_AGE_MILLIS] or don't fit into
 * [MAX_SIZE_BYTES] next to newer ones. Copies with a pending upload are kept. That is why [directory] must not be in the
 * cache directory, which the system clears when storage runs low. A copy returned by [file] or [fileForWriting] is kept
 * until it is [released][release], so that it can't be deleted before it is opened or while it is written.
 */
class DocumentContentCache(
	private val dao: CachedFileDao,
	private val metadataCache: DocumentMetadataCache,
	private val scheduler: CloudTaskScheduler,
	private val directory: File,
	private val watchClose: (file: File, onClosed: () -> Unit) -> AutoCloseable = ::watchCloseWrite,
	private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {

	private val resumedVaults: MutableSet<Long> = ConcurrentHashMap.newKeySet()

	/**
	 * How often each copy is in use; guarded by itself, also while copies are deleted.
	 */
	private val inUse = HashMap<String, Int>()

	/**
	 * Keeps the watches of detached writers reachable, by local name, because a garbage-collected FileObserver stops
	 * watching.
	 */
	private val detachedWriters = ConcurrentHashMap<String, AutoCloseable>()

	init {
		directory.mkdirs()
		evict()
	}

	fun file(path: VaultPath, signal: CancellationSignal? = null): File {
		val metadata = requireFile(metadataCache.requireCurrentDocument(path), path)
		val cached = dao.load(path.vault.id, path.path)
		if (cached != null && (metadata.isUploading || isSameSecond(cached.lastModified, metadata.lastModified))) {
			claimIfPresent(File(directory, cached.localName))?.let { return it }
		}
		evict()
		val downloadedFile = claim(File(directory, UUID.randomUUID().toString()))
		download(path, downloadedFile, signal)
		storeOrDrop(path, downloadedFile, metadata.lastModified)
		cached?.let { deleteUnlessInUse(File(directory, it.localName)) }
		return downloadedFile
	}

	/**
	 * Returns the local copy to write to. Its content is only fetched from the cloud if [keepContent] is set and the file
	 * already exists there.
	 */
	fun fileForWriting(path: VaultPath, keepContent: Boolean, signal: CancellationSignal? = null): File {
		val metadata = requireFile(metadataCache.requireDocument(path), path)
		if (keepContent && !metadata.isCreating) {
			return file(path, signal)
		}
		val localFile = localCopy(path)?.let { claimIfPresent(it) } ?: claim(File(directory, UUID.randomUUID().toString())).apply { createNewFile() }
		storeOrDrop(path, localFile, metadata.lastModified)
		return localFile
	}

	/**
	 * Handles a copy returned by [fileForWriting] being closed by its writer: a failed write drops the copy, a changed one
	 * is uploaded to where [currentPath] tells the file is now.
	 */
	fun closedForWriting(localFile: File, error: IOException?, changed: Boolean, currentPath: () -> VaultPath) {
		if (error is ParcelFileDescriptor.FileDescriptorDetachedException) {
			awaitDetachedWriter(localFile, currentPath)
			return
		}
		try {
			if (error != null) {
				discard(currentPath())
			} else if (changed) {
				upload(currentPath(), localFile)
			}
		} finally {
			release(localFile)
		}
	}

	/**
	 * A writer that detached the file descriptor, e.g. to hand it to native code, goes on writing without the provider
	 * noticing. Its copy is kept and marked as waiting for an upload, and uploaded once the writer closes the file.
	 */
	private fun awaitDetachedWriter(localFile: File, currentPath: () -> VaultPath) {
		metadataCache.markPendingUpload(currentPath())
		detachedWriters[localFile.name] = watchClose(localFile) {
			detachedWriters.remove(localFile.name)?.close()
			try {
				upload(currentPath(), localFile)
			} catch (e: Exception) {
				LOG.e(e, "Uploading a copy written through a detached file descriptor failed")
			} finally {
				release(localFile)
			}
		}
	}

	fun release(file: File) {
		synchronized(inUse) {
			val uses = inUse[file.name] ?: return
			if (uses > 1) inUse[file.name] = uses - 1 else inUse.remove(file.name)
		}
	}

	/**
	 * Uploads the local copy of [path]. If the file changed in the cloud since the version the copy is based on, the
	 * copy is uploaded next to it as a conflict copy and becomes the conflict copy's local copy.
	 */
	fun upload(path: VaultPath, localFile: File) {
		metadataCache.upload(path, localFile, { current -> dao.load(current.vault.id, current.path)?.lastModified }) { current, uploadedPath, lastModified ->
			if (uploadedPath == current) {
				dao.updateLastModified(current.vault.id, current.path, lastModified)
			} else {
				dao.delete(current.vault.id, current.path)
				store(uploadedPath, localFile, lastModified)
			}
		}
	}

	/**
	 * Drops the local copy of [path] after a failed write, together with the upload it was waiting for, unless someone
	 * other than the failed writer still uses it.
	 */
	fun discard(path: VaultPath) {
		synchronized(inUse) {
			val localFile = localCopy(path)
			if (localFile != null && (inUse[localFile.name] ?: 0) > 1) {
				LOG.w("Not dropping the copy of ${metadataCache.logId(path)} after a failed write, it is still in use")
				return
			}
			localFile?.delete()
			dao.delete(path.vault.id, path.path)
		}
		metadataCache.abandonUpload(path)
	}

	/**
	 * Retries the uploads of [vault] that did not finish. Only the first call per vault and process does so.
	 */
	fun resumeUploads(vault: Vault) {
		if (!resumedVaults.add(vault.id)) {
			return
		}
		metadataCache.pendingUploads(vault).forEach { path ->
			localCopy(path)?.let { upload(path, it) }
		}
	}

	/**
	 * Deletes the files without a cache row, and the copies that can be downloaded again and are too old or too many.
	 * Copies in use are kept.
	 */
	fun evict() {
		evict(MAX_COPY_AGE_MILLIS)
	}

	/**
	 * Deletes all copies but those with a pending upload or in use.
	 */
	fun evictAll() {
		evict(0)
	}

	private fun evict(maxAgeMillis: Long) {
		synchronized(inUse) {
			val localNames = dao.loadLocalNames().toSet()
			directory.listFiles()?.filter { it.name !in localNames && it.name !in inUse }?.forEach(::deleteCopy)
			var size = 0L
			dao.loadEvictable() //
				.filter { it.localName !in inUse } //
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
			release(localFile)
			deleteUnlessInUse(localFile)
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
	 */
	private fun download(path: VaultPath, localFile: File, signal: CancellationSignal?) {
		val transfer = scheduler.download(path) {
			signal?.throwIfCanceled()
			val cloudFile = contentRepository.metadata(contentRepository.file(parentFolder(path), path.name)) ?: throw FileNotFoundException("Document is gone from the cloud")
			localFile.outputStream().use { contentRepository.read(cloudFile, null, it, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD) }
		}
		try {
			transfer.await(signal)
		} catch (e: Exception) {
			transfer.whenComplete { _, _ ->
				release(localFile)
				deleteUnlessInUse(localFile)
			}
			throw e
		}
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
			onClosed()
		}
	}
	observer.startWatching()
	return AutoCloseable { observer.stopWatching() }
}
