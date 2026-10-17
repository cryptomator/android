package org.cryptomator.data.documents

import org.cryptomator.data.db.dao.CachedFileDao
import org.cryptomator.data.db.dao.DocumentMetadataDao
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.util.named
import java.io.File
import timber.log.Timber

private val LOG = Timber.named("ThumbnailStore")

private const val MAX_SIZE_BYTES = 50L * 1024 * 1024

/**
 * Previews of the images in vaults, one JPEG file per node and version of its content, in [directory]. Like the local
 * copies, they are kept for [MAX_COPY_AGE_MILLIS] after they were made, also while the vaults are locked. The directory
 * must not be in the cache directory, which the system clears when storage runs low.
 *
 * A synced file's version is its modification date in the cloud, compared to the second. A file whose write waits for its
 * upload is told apart by its local copy and when that was last written, so that a local edit gets a new preview before
 * it has been uploaded. The upload generation can't tell it, since a listing resets it.
 */
class ThumbnailStore @JvmOverloads constructor(
	private val directory: File,
	private val metadataDao: DocumentMetadataDao,
	private val cachedFileDao: CachedFileDao,
	private val copyDirectory: File,
	private val maxSizeBytes: Long = MAX_SIZE_BYTES,
	private val currentTimeMillis: () -> Long = System::currentTimeMillis
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
	 * Deletes the previews of nodes that are gone or whose content changed, those older than [MAX_COPY_AGE_MILLIS], and
	 * the oldest ones beyond the size limit. A node being deleted keeps its preview, since it comes back if the cloud
	 * rejects the deletion.
	 */
	fun evict() {
		synchronized(lock) {
			var size = 0L
			directory.listFiles()?.filterNot { isCurrent(it) && it.name.endsWith(".jpg") }?.forEach(::delete)
			directory.listFiles()?.sortedByDescending { it.lastModified() }?.forEach { file ->
				size += file.length()
				if (currentTimeMillis() - file.lastModified() >= MAX_COPY_AGE_MILLIS || size > maxSizeBytes) {
					delete(file)
				}
			}
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

	private fun isCurrent(file: File): Boolean {
		val parts = file.name.removeSuffix(".jpg").split('_', limit = 3)
		if (parts.size != 3) {
			return false
		}
		val metadata = parts[1].toLongOrNull()?.let(metadataDao::loadById) ?: return false
		return metadata.vaultId.toString() == parts[0] && version(metadata) == parts[2]
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
	}
}
