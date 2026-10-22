package org.cryptomator.data.documents

import android.content.Context
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.CachedVaultFile
import org.cryptomator.domain.CachedVaultFolder
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.CancellationException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.IllegalFileNameException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.CloudRepository
import org.cryptomator.domain.repository.VaultDocumentChange
import org.cryptomator.domain.repository.VaultDocumentChangeResult
import org.cryptomator.domain.repository.VaultDocumentImport
import org.cryptomator.domain.repository.VaultDocumentListener
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.cryptomator.domain.repository.VaultListing
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.util.Date
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton
import dagger.Lazy

/**
 * The caches are created on first use rather than when the repository is injected at the start of the app, since
 * creating them cleans up the database and the copy directory.
 */
@Singleton
class VaultDocumentRepositoryImpl @Inject constructor(
	private val context: Context,
	private val lazyMetadataCache: Lazy<DocumentMetadataCache>,
	private val lazyContentCache: Lazy<DocumentContentCache>,
	private val lazyThumbnailStore: Lazy<ThumbnailStore>,
	private val lazyThumbnailGenerator: Lazy<ThumbnailGenerator>,
	private val events: VaultDocumentEvents,
	private val cloudRepository: CloudRepository
) : VaultDocumentRepository {

	private val metadataCache: DocumentMetadataCache
		get() = lazyMetadataCache.get()

	private val contentCache: DocumentContentCache
		get() = lazyContentCache.get()

	override fun pathOf(node: CloudNode): VaultPath? {
		return (node.cloud as? CryptoCloud)?.let { VaultPath(it.vault, node.path) }
	}

	override fun list(folder: CloudFolder, forceRefresh: Boolean): VaultListing = cacheExceptions {
		val path = requireNotNull(pathOf(folder)) { "Not inside a vault" }
		if (forceRefresh) {
			metadataCache.listNow(path)
		} else {
			metadataCache.ensureFolder(path)
		}
		val children = metadataCache.children(path)
		val parent = CachedVaultFolder(folder.cloud, nodePath(path))
		VaultListing(children.documents.map { node(parent, it) }, children.isLoading, children.error ?: children.authError)
	}

	/**
	 * The node with [documentId] as the cache knows it, wherever it is by now, or `null` if it is gone.
	 */
	private fun cachedNode(documentId: String): CloudNode? {
		val path = try {
			metadataCache.path(documentId)
		} catch (e: FileNotFoundException) {
			return null
		}
		// another node may have taken the path since it was looked up
		val metadata = metadataCache.cachedDocument(path)?.takeIf { metadataCache.documentId(it) == documentId } ?: return null
		val parent = CachedVaultFolder(cloudRepository.decryptedViewOf(path.vault), nodePath(requireNotNull(path.parent)))
		return node(parent, metadata)
	}

	override fun resolve(folder: CloudFolder): CloudFolder = cacheExceptions {
		metadataCache.cloudFolder(requireNotNull(pathOf(folder)) { "Not inside a vault" })
	}

	private fun node(parent: CachedVaultFolder, metadata: DocumentMetadataEntity): CloudNode {
		if (metadata.isFolder) {
			return CachedVaultFolder(parent.cloud, "${parent.path}/${metadata.name}")
		}
		val upload = when {
			!metadata.isUploading -> CachedVaultFile.Upload.NONE
			metadataCache.uploadFailure(metadata) != null -> CachedVaultFile.Upload.FAILED
			else -> CachedVaultFile.Upload.PENDING
		}
		val thumbnail = metadata.takeIf { it.isImage }?.let(lazyThumbnailStore.get()::thumbnail)
		return CachedVaultFile(parent, metadata.name, metadata.size, metadata.lastModified?.let(::Date), upload, thumbnail)
	}

	/**
	 * [path] in the form of [CloudNode.path] within the vault: `/a/b`, or `` for the root.
	 */
	private fun nodePath(path: VaultPath): String {
		return if (path.isRoot) "" else "/${path.path}"
	}

	override fun createFolder(folder: VaultPath, name: String, userInitiated: Boolean): VaultDocumentChange<CloudFolder> = cacheExceptions {
		val creation = metadataCache.create(folder, name, isFolder = true, failIfTaken = true, userInitiated = userInitiated)
		change(creation.result, creation.documentId) { VaultDocumentChangeResult(it.path, it.node as CloudFolder) }
	}

	override fun rename(path: VaultPath, name: String, userInitiated: Boolean): VaultDocumentChange<CloudNode> = cacheExceptions {
		val documentId = metadataCache.documentId(metadataCache.requireKnownDocument(path))
		change(metadataCache.move(path, requireNotNull(path.parent), name, failIfTaken = true, userInitiated = userInitiated), documentId) { it }
	}

	override fun move(path: VaultPath, folder: VaultPath, userInitiated: Boolean): VaultDocumentChange<CloudNode> = cacheExceptions {
		val documentId = metadataCache.documentId(metadataCache.requireKnownDocument(path))
		change(metadataCache.move(path, folder, path.name, failIfTaken = true, userInitiated = userInitiated), documentId) { it }
	}

	override fun delete(path: VaultPath, userInitiated: Boolean): VaultDocumentChange<Unit> = cacheExceptions {
		metadataCache.requireKnownDocument(path)
		change(contentCache.delete(path, userInitiated).result, documentId = null) { VaultDocumentChangeResult(path, Unit) }
	}

	/**
	 * A name is a single path element, also when another app chose it.
	 */
	override fun importFile(folder: VaultPath, name: String, data: DataSource, replace: Boolean, requireListing: Boolean, userInitiated: Boolean, progressAware: ProgressAware<UploadState>): VaultDocumentImport {
		if (name.contains('/')) {
			throw IllegalFileNameException()
		}
		val upload = cacheExceptions { contentCache.importFile(folder, name, replace, requireListing, userInitiated, progressAware, data.modifiedDate(context).map { it.time }.orElse(null)) { output ->
			try {
				(data.open(context) ?: throw FileNotFoundException("No data to import into ${metadataCache.logId(folder)}")).use { it.copyTo(output) }
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		} }
		val change = change(upload.result, upload.documentId) { it }
		return object : VaultDocumentImport, VaultDocumentChange<CloudFile> by change {
			override fun cancel() {
				contentCache.cancel(upload)
			}
		}
	}

	override fun read(path: VaultPath, target: OutputStream, progressAware: ProgressAware<DownloadState>) {
		cacheExceptions {
			metadataCache.requireKnownDocument(path)
			val localFile = contentCache.file(path, progressAware = progressAware)
			try {
				localFile.inputStream().use { it.copyTo(target) }
			} catch (e: IOException) {
				throw FatalBackendException(e)
			} finally {
				contentCache.release(localFile)
			}
		}
	}

	override fun open(path: VaultPath, progressAware: ProgressAware<DownloadState>): String = cacheExceptions {
		metadataCache.requireKnownDocument(path)
		contentCache.prepare(path, progressAware)
		requireNotNull(metadataCache.documentId(path))
	}

	override fun startExternalEditing(documentId: String) {
		cacheExceptions {
			contentCache.pinBase(documentId)
		}
	}

	override fun endExternalEditing(documentId: String) {
		contentCache.unpinBase(documentId)
	}

	override fun resumeUploadsOnce(vault: Vault) {
		contentCache.resumeUploadsOnce(vault)
	}

	override fun retryUploads(vault: Vault, paths: List<VaultPath>) {
		contentCache.resumeUploads(vault, paths, userInitiated = true)
	}

	override fun ensureThumbnails(files: List<CloudFile>) {
		lazyThumbnailGenerator.get().request(files.mapNotNull(::pathOf))
	}

	override fun clearCache() {
		contentCache.evictAll()
	}

	override fun cacheSize(): Long {
		return contentCache.size() + lazyThumbnailStore.get().size()
	}

	override fun addListener(listener: VaultDocumentListener) {
		events.addListener(listener)
	}

	override fun removeListener(listener: VaultDocumentListener) {
		events.removeListener(listener)
	}

	/**
	 * [documentId] is the changed node's, `null` for a deletion. A change that was stopped, e.g. because its file was
	 * deleted, ends like a canceled use case.
	 */
	private fun <T, R> change(result: CompletableFuture<T>, documentId: String?, toResult: (T) -> VaultDocumentChangeResult<R>): VaultDocumentChange<R> {
		return object : VaultDocumentChange<R> {
			@Suppress("UNCHECKED_CAST")
			override fun cachedNode(): R? = documentId?.let { cachedNode(it) as R? }

			override fun await(): VaultDocumentChangeResult<R> {
				try {
					return toResult(result.await())
				} catch (e: java.util.concurrent.CancellationException) {
					throw CancellationException(e)
				}
			}
		}
	}

	/**
	 * The caches tell the documents provider about a node that vanished meanwhile like Files expects it, with a
	 * [FileNotFoundException].
	 */
	private inline fun <T> cacheExceptions(block: () -> T): T {
		try {
			return block()
		} catch (e: FileNotFoundException) {
			throw NoSuchCloudFileException(e.message)
		}
	}
}
