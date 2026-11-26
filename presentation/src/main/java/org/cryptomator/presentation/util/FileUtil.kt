package org.cryptomator.presentation.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.usecases.cloud.MicrosoftWorkaroundCopies
import org.cryptomator.presentation.model.AutoUploadFilesStore
import org.cryptomator.presentation.model.ImagePreviewFilesStore
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypes
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.UUID
import javax.inject.Inject
import timber.log.Timber

class FileUtil @Inject constructor(private val context: Context, private val mimeTypes: MimeTypes) {

	private var decryptedFileStorage: File = File(context.cacheDir, "decrypted")

	/**
	 * Deletes old files in the cache directory, and copies the workaround for Microsoft apps left in shared storage that
	 * weren't changed for [MICROSOFT_WORKAROUND_COPY_RETENTION_MILLISECONDS], e.g. one whose changes couldn't be saved to
	 * the vault, or one Cryptomator was killed while it was edited. A more recent copy may still be edited. It also deletes
	 * the encrypted per-cloud caches in `LruCache*` folders that earlier versions left behind.
	 */
	fun cleanup() {
		// asking for the media folders creates them, so they are deleted again unless something else is in them
		val mediaDirs = context.externalMediaDirs.filterNotNull()
		context.cacheDir.listFiles { file -> file.isDirectory && file.name.startsWith("LruCache") }.orEmpty().forEach { legacy ->
			if (!legacy.deleteRecursively()) {
				Timber.w("Failed to delete legacy cache ${legacy.name}")
			}
		}
		mediaDirs.map(::microsoftWorkaroundDir).forEach { copies ->
			cleanupDir(copies, MICROSOFT_WORKAROUND_COPY_RETENTION_MILLISECONDS)
			// each copy has a folder of its own
			copies.listFiles { file -> file.isDirectory }.orEmpty().forEach(::deleteIfEmpty)
			deleteIfEmpty(copies)
		}
		mediaDirs.forEach(::deleteIfEmpty)
		cleanupDir(context.cacheDir, CLEANUP_AFTER_MILLISECONDS)
	}

	private fun deleteIfEmpty(directory: File) {
		if (directory.list()?.isEmpty() == true) {
			directory.delete()
		}
	}

	private fun microsoftWorkaroundDir(mediaDir: File): File {
		return File(mediaDir, "decrypted")
	}

	private fun cleanupDir(directory: File, retentionMilliseconds: Long) {
		directory.listFiles()?.forEach { child ->
			if (child.isDirectory) {
				cleanupDir(child, retentionMilliseconds)
			} else {
				cleanupFile(child, retentionMilliseconds)
			}
		}
	}

	private fun cleanupFile(file: File, retentionMilliseconds: Long) {
		val cleanupFilesOlderThan = System.currentTimeMillis() - retentionMilliseconds
		if (file.lastModified() < cleanupFilesOlderThan && file.name != AUTO_UPLOAD_IMAGE__FILE_NAMES) {
			if (!file.delete()) {
				Timber.w("Failed to cleanup file in cacheDir")
			}
		}
	}

	fun contentUriForNewTempFile(fileName: String): Uri {
		decryptedFileStorage.mkdir()
		val file = File(decryptedFileStorage, fileName)
		return FileProvider.getUriForFile(context, context.applicationContext.packageName + ".fileprovider", file)
	}

	fun tempFile(fileName: String): File {
		decryptedFileStorage.mkdir()
		return File(decryptedFileStorage, fileName)
	}

	/**
	 * Where the copy of [fileName] goes that a Microsoft app gets, since such an app can only save a file in shared
	 * storage. Creates the copy's own folder, see [MicrosoftWorkaroundCopies].
	 *
	 * @return `null` if the app's media folder on the primary shared storage isn't available or writable
	 */
	fun microsoftWorkaroundCopyFor(fileName: String): File? {
		val mediaDir = context.externalMediaDirs.firstOrNull()?.takeIf { it.canWrite() }
		if (mediaDir == null) {
			Timber.tag("FileUtil").e("Media storage isn't available or writable")
			return null
		}
		val folder = File(microsoftWorkaroundDir(mediaDir), UUID.randomUUID().toString())
		folder.mkdirs()
		val extension = getExtension(fileName).orEmpty()
		return File(folder, fileName.removeSuffix(extension) + extension.lowercase())
	}

	fun fileInfo(name: String): FileInfo {
		return FileInfo(name, mimeTypes)
	}

	fun storeImagePreviewFiles(imagePreviewFilesStore: ImagePreviewFilesStore?): String? {
		decryptedFileStorage.mkdir()
		val file = File(decryptedFileStorage, IMAGE_PREVIEW__FILE_NAMES)
		try {
			ObjectOutputStream(FileOutputStream(file.path)).use { out ->
				out.writeObject(imagePreviewFilesStore)
				out.close()
				return file.path
			}
		} catch (e: IOException) {
			Timber //
				.tag("FileUtil") //
				.e(e, "Failed to store image preview file list for PreviewActivity")
			throw FatalBackendException(e)
		}
	}

	fun getImagePreviewFiles(path: String): ImagePreviewFilesStore {
		try {
			ObjectInputStream(FileInputStream(path)).use { objectInputStream ->
				return objectInputStream.readObject() as ImagePreviewFilesStore
			}
		} catch (e: ClassNotFoundException) {
			Timber //
				.tag("FileUtil") //
				.e(e, "Failed to read image preview file from list for PreviewActivity")
			throw FatalBackendException(e)
		} catch (e: IOException) {
			Timber
				.tag("FileUtil")
				.e(e, "Failed to read image preview file from list for PreviewActivity")
			throw FatalBackendException(e)
		}
	}

	fun addImageToAutoUploads(path: String): AutoUploadFilesStore {
		val paths = getAutoUploadFilesStore().uris + path
		return addImageToAutoUploads(paths)
	}

	private fun addImageToAutoUploads(paths: Set<String>): AutoUploadFilesStore {
		return addImageToAutoUploads(AutoUploadFilesStore(paths))
	}

	@Synchronized
	private fun addImageToAutoUploads(autoUploadFilesStore: AutoUploadFilesStore): AutoUploadFilesStore {
		try {
			decryptedFileStorage.mkdir()

			val file = File(decryptedFileStorage, AUTO_UPLOAD_IMAGE__FILE_NAMES)

			ObjectOutputStream(FileOutputStream(file.path)).use { objectOutputStream ->
				objectOutputStream.writeObject(autoUploadFilesStore)
				objectOutputStream.close()
			}

			return autoUploadFilesStore
		} catch (e: IOException) {
			Timber //
				.tag("FileUtil") //
				.e(e, "Failed to store image preview file list for PreviewActivity")
			throw FatalBackendException(e)
		}
	}

	@Synchronized
	fun getAutoUploadFilesStore(): AutoUploadFilesStore {
		val file = File(decryptedFileStorage, AUTO_UPLOAD_IMAGE__FILE_NAMES)
		if (!file.exists()) {
			return AutoUploadFilesStore(HashSet())
		}
		try {
			ObjectInputStream(FileInputStream(file)).use { objectInputStream ->
				var autoUploadFilesStore = objectInputStream.readObject() as AutoUploadFilesStore
				autoUploadFilesStore = AutoUploadFilesStore(autoUploadFilesStore.uris.filter { uri -> File(uri).exists() }.toSet())
				return autoUploadFilesStore
			}
		} catch (e: InvalidClassException) {
			return tryRecoverAutoUploadFilesStoreDueToFileObfuscation(file)
		} catch (e: ClassCastException) {
			return tryRecoverAutoUploadFilesStoreDueToFileObfuscation(file)
		} catch (e: IOException) {
			Timber
				.tag("FileUtil")
				.e(e, "Failed to read image preview file from list for PreviewActivity")
			throw FatalBackendException(e)
		}
	}

	/**
	 * This method tries to recover the AutoUploadFilesStore which was obfuscated in version 1.5.10 and 1.5.11-beta1, each differently
	 */
	private fun tryRecoverAutoUploadFilesStoreDueToFileObfuscation(file: File): AutoUploadFilesStore {
		Timber.tag("FileUtil").i("Try to recover AutoUploadFilesStore using class c or a")
		ObjectInputStream(FileInputStream(file)).use { objectInputStream ->
			val uploadPaths = when (val obj = objectInputStream.readObject()) {
				is org.cryptomator.presentation.e.c -> obj.mE() // version 1.5.10
				is org.cryptomator.presentation.i.a -> obj.b() // version 1.5.11-beta1
				else -> null
			}
			when {
				uploadPaths != null -> {
					Timber.tag("FileUtil").i("Nailed it! Successfully recovered AutoUploadFilesStore!")
					file.delete()
					return AutoUploadFilesStore(uploadPaths)
				}
				else -> throw FatalBackendException("Failed to recover AutoUploadFilesStore")
			}
		}
	}

	@Synchronized
	fun removeImagesFromAutoUploads(names: Set<String>): AutoUploadFilesStore {
		val autoUploadFilesStore = getAutoUploadFilesStore()
		var paths = autoUploadFilesStore.uris

		if (autoUploadFilesStore.uris.isEmpty()) {
			return autoUploadFilesStore
		}

		val dirPath = File(autoUploadFilesStore.uris.iterator().next()).parent
		names.forEach { name ->
			paths = paths.minus(String.format("%s/%s", dirPath, name))
		}

		return addImageToAutoUploads(paths)
	}

	class FileInfo(val name: String, mimeTypes: MimeTypes) {

		var extension: String?
		var mimeType: MimeType

		init {
			val lastDot = name.lastIndexOf('.')
			if (lastDot == -1 || lastDot == name.length - 1) {
				extension = null
				mimeType = MimeType.APPLICATION_OCTET_STREAM
			} else {
				extension = name.substring(lastDot + 1)
				mimeType = extension?.let { mimeTypes.fromExtension(it) } ?: MimeType.APPLICATION_OCTET_STREAM
			}
		}
	}

	companion object {

		private const val MILLISECONDS_PER_HOUR = 60 * 60 * 1000L
		private const val CLEANUP_AFTER_MILLISECONDS = 5 * MILLISECONDS_PER_HOUR

		/**
		 * How long a copy the workaround for Microsoft apps left in shared storage is kept after its last change, since
		 * its changes may not be in the vault yet. The disclaimer of the setting tells the user about it.
		 */
		private const val MICROSOFT_WORKAROUND_COPY_RETENTION_MILLISECONDS = 5 * MILLISECONDS_PER_HOUR
		private const val IMAGE_PREVIEW__FILE_NAMES = "imagePreviewFilenames"
		private const val AUTO_UPLOAD_IMAGE__FILE_NAMES = "autoUploadImageFileNames"

		fun getExtension(fileName: String): String? {
			val extensionSeparatorIndex = fileName.lastIndexOf(".")
			return if (extensionSeparatorIndex != -1) fileName.substring(extensionSeparatorIndex + 1) else null
		}
	}

}
