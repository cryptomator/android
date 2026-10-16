package org.cryptomator.data.documents

import android.content.Context
import org.cryptomator.data.cloud.crypto.CryptoCloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.CancellationException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.IllegalFileNameException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.repository.VaultDocumentChange
import org.cryptomator.domain.repository.VaultDocumentChangeResult
import org.cryptomator.domain.repository.VaultDocumentImport
import org.cryptomator.domain.repository.VaultDocumentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.FileNotFoundException
import java.io.IOException
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
	private val lazyContentCache: Lazy<DocumentContentCache>
) : VaultDocumentRepository {

	private val metadataCache: DocumentMetadataCache
		get() = lazyMetadataCache.get()

	private val contentCache: DocumentContentCache
		get() = lazyContentCache.get()

	override fun pathOf(node: CloudNode): VaultPath? {
		return (node.cloud as? CryptoCloud)?.let { VaultPath(it.vault, node.path) }
	}

	override fun createFolder(folder: VaultPath, name: String): VaultDocumentChange<CloudFolder> = cacheExceptions {
		change(metadataCache.create(folder, name, isFolder = true, failIfTaken = true).result) { VaultDocumentChangeResult(it.path, it.node as CloudFolder) }
	}

	override fun rename(path: VaultPath, name: String): VaultDocumentChange<CloudNode> = cacheExceptions {
		metadataCache.requireKnownDocument(path)
		change(metadataCache.move(path, requireNotNull(path.parent), name, failIfTaken = true)) { it }
	}

	override fun move(path: VaultPath, folder: VaultPath): VaultDocumentChange<CloudNode> = cacheExceptions {
		metadataCache.requireKnownDocument(path)
		change(metadataCache.move(path, folder, path.name, failIfTaken = true)) { it }
	}

	override fun delete(path: VaultPath): VaultDocumentChange<Unit> = cacheExceptions {
		metadataCache.requireKnownDocument(path)
		change(contentCache.delete(path).result) { VaultDocumentChangeResult(path, Unit) }
	}

	/**
	 * A name is a single path element, also when another app chose it.
	 */
	override fun importFile(folder: VaultPath, name: String, data: DataSource, replace: Boolean, requireListing: Boolean, progressAware: ProgressAware<UploadState>): VaultDocumentImport {
		if (name.contains('/')) {
			throw IllegalFileNameException()
		}
		val upload = cacheExceptions { contentCache.importFile(folder, name, replace, requireListing, progressAware, data.modifiedDate(context).map { it.time }.orElse(null)) { output ->
			try {
				(data.open(context) ?: throw FileNotFoundException("No data to import into ${metadataCache.logId(folder)}")).use { it.copyTo(output) }
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		} }
		val change = change(upload.result) { it }
		return object : VaultDocumentImport, VaultDocumentChange<CloudFile> by change {
			override fun cancel() {
				contentCache.cancel(upload)
			}
		}
	}

	override fun resumeUploads(vault: Vault) {
		contentCache.resumeUploads(vault)
	}

	/**
	 * A change that was stopped, e.g. because its file was deleted, ends like a canceled use case.
	 */
	private fun <T, R> change(result: CompletableFuture<T>, toResult: (T) -> VaultDocumentChangeResult<R>): VaultDocumentChange<R> {
		return VaultDocumentChange {
			try {
				toResult(result.await())
			} catch (e: java.util.concurrent.CancellationException) {
				throw CancellationException(e)
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
