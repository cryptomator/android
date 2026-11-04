package org.cryptomator.data.documents

import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.util.named
import java.io.File
import timber.log.Timber

private val LOG = Timber.named("ThumbnailStore")

/**
 * Previews of the images in vaults, one JPEG file per node and version of its content, in [directory]. Like the local
 * copies, they are kept until their node or its content changes or the cache is cleared, also while the vaults are
 * locked. The directory must not be in the cache directory, which the system clears when storage runs low.
 *
 * A synced file's version is its modification date in the cloud, compared to the second. A file whose write waits for its
 * upload is told apart by its local copy and when that was last written, so that a local edit gets a new preview before
 * it has been uploaded. The upload generation can't tell it, since a listing resets it.
 */
class ThumbnailStore @JvmOverloads constructor(
	private val directory: File,
	private val metadataDao: DocumentMetadataDao,
	private val cachedFileDao: CachedFileDao,
	private val copyDirectory: File
) {

	private val lock = Any()

	/**
	 * The version of [metadata]'s content, or null if there is no way to tell it, e.g. a new file that is still empty.
	 */
	fun version(metadata: DocumentMetadataEntity): String? {
		if (metadata.isUploading) {
			val cached = cachedFileDao.load(metadata.vaultId, metadata.path) ?: return null
			return "${cached.localName}-${File(copyDirectory, cached.localName).lastModified()}"
		}
		return metadata.lastModified?.let { "m${it / 1000}" }
	}

	/**
	 * The preview of [metadata]'s current content, if it has been made.
	 */
	fun thumbnail(metadata: DocumentMetadataEntity): File? {
		val version = version(metadata) ?: return null
		return file(metadata, version).takeIf { it.exists() }
	}

	fun put(metadata: DocumentMetadataEntity, version: String, jpeg: ByteArray) {
		synchronized(lock) {
			directory.mkdirs()
			val written = File(directory, "${file(metadata, version).name}.tmp")
			written.writeBytes(jpeg)
			if (!written.renameTo(file(metadata, version))) {
				written.delete()
				LOG.w("Failed to store the thumbnail of ${metadata.vaultId}:${metadata.id}")
			}
		}
	}

	/**
	 * Deletes the previews of nodes that are gone or whose content changed. A node being deleted keeps its preview, since
	 * it comes back if the cloud rejects the deletion.
	 */
	fun evict() {
		synchronized(lock) {
			val files = directory.listFiles() ?: return
			val current = files.mapNotNull(::metadataId).distinct().chunked(MAX_QUERY_VARIABLES).flatMap(metadataDao::loadByIds) //
				.mapNotNullTo(HashSet()) { metadata -> version(metadata)?.let { file(metadata, it).name } }
			files.filterNot { it.name in current }.forEach(::delete)
		}
	}

	/**
	 * Deletes the previews of the nodes with [documentIds], e.g. once they have been deleted.
	 */
	fun delete(documentIds: List<String>) {
		val prefixes = documentIds.map { "${it.replace(':', '_')}_" }
		synchronized(lock) {
			directory.listFiles { file -> prefixes.any(file.name::startsWith) }?.forEach(::delete)
		}
	}

	fun clear() {
		synchronized(lock) {
			directory.listFiles()?.forEach(::delete)
		}
	}

	fun size(): Long {
		return directory.listFiles()?.sumOf { it.length() } ?: 0L
	}

	/**
	 * The node ID in a thumbnail's name, which is `<vault ID>_<node ID>_<version>.jpg`.
	 */
	private fun metadataId(file: File): Long? {
		return file.name.split('_', limit = 3).getOrNull(1)?.toLongOrNull()
	}

	private fun file(metadata: DocumentMetadataEntity, version: String): File {
		return File(directory, "${metadata.vaultId}_${metadata.id}_$version.jpg")
	}

	private fun delete(file: File) {
		if (!file.delete()) {
			LOG.w("Failed to delete thumbnail ${file.name}")
		}
	}

	companion object {

		const val DIRECTORY_NAME = "thumbnails"

		/**
		 * What SQLite accepts in one statement on the oldest supported Android version.
		 */
		private const val MAX_QUERY_VARIABLES = 999
	}
}
