package org.cryptomator.presentation.util

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import androidx.core.content.FileProvider
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.presentation.model.AutoUploadFilesStore
import org.cryptomator.presentation.model.CloudFileModel
import org.cryptomator.presentation.model.ImagePreviewFilesStore
import org.cryptomator.util.Optional
import org.cryptomator.util.file.LruFileCacheUtil
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypes
import timber.log.Timber
import java.io.*
import java.util.*
import javax.inject.Inject

class FileUtil @Inject constructor(private val context: Context, private val mimeTypes: MimeTypes) {

	private var decryptedFileStorage: File = File(context.cacheDir, "decrypted")

	fun cleanup() {
		cleanupDir(context.cacheDir)
	}

	fun cleanupDecryptedFiles() {
		if (decryptedFileStorage.exists()) {
			decryptedFileStorage.listFiles()
					?.filter { it.name != AUTO_UPLOAD_IMAGE__FILE_NAMES && !it.delete() }
					?.forEach { Timber.w("Failed to cleanup file in decryptedFileStorage") }
		}
	}

	private fun cleanupDir(directory: File) {
		directory.listFiles()?.forEach { child ->
			if (child.isDirectory) {
				if (!lruCacheFolder(child)) {
					cleanupDir(child)
				}
			} else {
				cleanupFile(child)
			}
		}
	}

	private fun lruCacheFolder(child: File): Boolean {
		val lruFileCacheUtil = LruFileCacheUtil(context)
		LruFileCacheUtil.Cache.values().forEach { cache ->
			if (lruFileCacheUtil.resolve(cache) == child) {
				return true
			}
		}
		return false
	}

	private fun cleanupFile(file: File) {
		val cleanupFilesOlderThan = System.currentTimeMillis() - CLEANUP_AFTER_MILLISECONDS
		if (file.lastModified() < cleanupFilesOlderThan && file.name != AUTO_UPLOAD_IMAGE__FILE_NAMES) {
			if (!file.delete()) {
				Timber.w("Failed to cleanup file in cacheDir")
			}
		}
	}

	@Throws(FileNotFoundException::class)
	fun newDecryptedData(cloudFile: CloudFileModel): OutputStream {
		val decryptedFile = fileFor(cloudFile)
		return FileOutputStream(decryptedFile)
	}

	fun contentUriFor(cloudFile: CloudFileModel): Uri {
		val decryptedFile = fileFor(cloudFile)
		check(decryptedFile.exists()) { "Decrypted cloud file did not exist but was expected to" }
		return FileProvider.getUriForFile(context, context.applicationContext.packageName + ".fileprovider", decryptedFile)
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

	fun contentUrisFor(cloudFiles: List<CloudFileModel>): ArrayList<out Parcelable?> {
		return cloudFiles.mapTo(ArrayList()) { contentUriFor(it) }
	}

	fun fileFor(cloudFile: CloudFileModel): File {
		decryptedFileStorage.mkdir()
		return File(decryptedFileStorage, fileNameLowerCaseExtension(cloudFile))
	}

	private fun fileNameLowerCaseExtension(cloudFile: CloudFileModel): String {
		val cloudFileName = cloudFile.name
		val extension = getExtension(cloudFileName)
		return if (extension != null) getSimpleFileName(cloudFileName) + "." + extension.toLowerCase(Locale.ROOT) else cloudFileName
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
		try {
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
		} catch (e: Exception) {
			throw FatalBackendException("Failed to recover AutoUploadFilesStore", e)
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
		var extension: Optional<String>
		var mimeType: MimeType

		init {
			val lastDot = name.lastIndexOf('.')
			if (lastDot == -1 || lastDot == name.length - 1) {
				extension = Optional.empty()
				mimeType = MimeType.APPLICATION_OCTET_STREAM
			} else {
				extension = Optional.of(name.substring(lastDot + 1))
				mimeType = mimeTypes.fromExtension(extension.get()).orElse(MimeType.APPLICATION_OCTET_STREAM)
			}
		}
	}

	companion object {

		private const val MILLISECONDS_PER_HOUR = 60 * 60 * 1000L
		private const val CLEANUP_AFTER_MILLISECONDS = 5 * MILLISECONDS_PER_HOUR
		private const val IMAGE_PREVIEW__FILE_NAMES = "imagePreviewFilenames"
		private const val AUTO_UPLOAD_IMAGE__FILE_NAMES = "autoUploadImageFileNames"

		private fun getSimpleFileName(fileName: String): String {
			val extensionSeparatorIndex = fileName.lastIndexOf(".")
			return if (extensionSeparatorIndex != -1) fileName.substring(0, extensionSeparatorIndex) else fileName
		}

		fun getExtension(fileName: String): String? {
			val extensionSeparatorIndex = fileName.lastIndexOf(".")
			return if (extensionSeparatorIndex != -1) fileName.substring(extensionSeparatorIndex + 1) else null
		}
	}

}
