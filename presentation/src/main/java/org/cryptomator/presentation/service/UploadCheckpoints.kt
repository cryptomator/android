package org.cryptomator.presentation.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import timber.log.Timber
import org.cryptomator.data.db.dao.UploadCheckpointDao
import org.cryptomator.data.db.entities.UploadCheckpointEntity
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.repository.CloudRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The checkpoints of folder uploads, at most one per vault, with the files uploaded so far. A checkpoint outlives an
 * interrupted upload, so that the upload can be resumed.
 */
@Singleton
class UploadCheckpoints @Inject constructor(
	private val context: Context,
	private val uploadCheckpointDao: UploadCheckpointDao,
	private val cloudRepository: CloudRepository
) {

	fun find(vaultId: Long): UploadCheckpointEntity? = uploadCheckpointDao.findByVaultId(vaultId)

	fun exists(vaultId: Long): Boolean = uploadCheckpointDao.existsForVault(vaultId)

	fun vaultIds(): List<Long> = uploadCheckpointDao.findAllVaultIdsWithCheckpoints()

	/**
	 * Also keeps the read access to the source folder, so that the upload can be resumed after a restart.
	 *
	 * @return `false` if the vault has a checkpoint already
	 */
	@Synchronized
	fun createIfAbsent(checkpoint: UploadCheckpointEntity): Boolean {
		if (exists(checkpoint.vaultId)) {
			return false
		}
		takeReadAccess(checkpoint.sourceFolderUri)
		return uploadCheckpointDao.insertIfAbsent(checkpoint) != -1L
	}

	fun completedFiles(vaultId: Long): Set<String> = uploadCheckpointDao.findCompletedFiles(vaultId).toHashSet()

	fun completedFileCount(vaultId: Long): Int = uploadCheckpointDao.countCompletedFiles(vaultId)

	fun recordCompleted(vaultId: Long, relativePath: String) {
		uploadCheckpointDao.insertCompletedFile(vaultId, relativePath)
	}

	/**
	 * Also releases the read access to the source folder, unless another checkpoint or a local storage location uses it.
	 */
	@Synchronized
	fun delete(vaultId: Long) {
		val sourceFolderUri = uploadCheckpointDao.findSourceFolderUri(vaultId)
		uploadCheckpointDao.deleteByVaultId(vaultId)
		sourceFolderUri?.let(::releaseReadAccessUnlessUsed)
	}

	private fun takeReadAccess(sourceFolderUri: String) {
		try {
			context.contentResolver.takePersistableUriPermission(Uri.parse(sourceFolderUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
		} catch (e: SecurityException) {
			// the upload still reads the folder through the picker's grant, but can't be resumed after a restart
			Timber.tag("UploadCheckpoints").w(e, "Failed to keep the read access to the source folder")
		}
	}

	private fun releaseReadAccessUnlessUsed(sourceFolderUri: String) {
		if (uploadCheckpointDao.countBySourceFolderUri(sourceFolderUri) == 0 && !isLocalStorageLocation(sourceFolderUri)) {
			try {
				context.contentResolver.releasePersistableUriPermission(Uri.parse(sourceFolderUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
			} catch (e: SecurityException) {
				Timber.tag("UploadCheckpoints").d(e, "No persisted read access to release for %s", sourceFolderUri)
			}
		}
	}

	private fun isLocalStorageLocation(uri: String): Boolean {
		return try {
			cloudRepository.clouds(CloudType.LOCAL).filterIsInstance<LocalStorageCloud>().any { it.rootUri() == uri }
		} catch (e: BackendException) {
			Timber.tag("UploadCheckpoints").e(e, "Failed to load local storage locations, keeping read access")
			true
		}
	}
}
