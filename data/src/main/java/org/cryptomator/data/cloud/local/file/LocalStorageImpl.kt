package org.cryptomator.data.cloud.local.file

import android.content.Context
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Date

internal class LocalStorageImpl(private val context: Context, localStorageCloud: LocalStorageCloud) {

	private val root: RootLocalFolder = RootLocalFolder(localStorageCloud)

	fun root(): LocalFolder {
		return root
	}

	fun resolve(path: String): LocalFolder {
		val names = path.substring(root.path.length + 1).split("/").toTypedArray()
		var folder: LocalFolder = root
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	fun file(folder: LocalFolder, name: String, size: Long?): LocalFile {
		return LocalStorageNodeFactory.file(folder, name, folder.path + '/' + name, size, null)
	}

	fun folder(folder: LocalFolder, name: String): LocalFolder {
		return LocalStorageNodeFactory.folder(folder, name, folder.path + '/' + name)
	}

	fun exists(node: CloudNode): Boolean {
		return File(node.path).exists()
	}

	@Throws(BackendException::class)
	fun list(folder: LocalFolder): List<LocalNode> {
		val localDirectory = File(folder.path)
		if (!exists(folder)) {
			throw NoSuchCloudFileException()
		}
		return localDirectory.listFiles()?.map { file -> LocalStorageNodeFactory.from(folder, file) }
			?: throw FatalBackendException("listFiles() shouldn't return null")
	}

	@Throws(BackendException::class)
	fun create(folder: LocalFolder): LocalFolder {
		folder.parent?.let { parentFolder ->
			val createFolder = File(folder.path)
			if (createFolder.exists()) {
				throw CloudNodeAlreadyExistsException(folder.name)
			}
			if (!createFolder.mkdirs()) {
				throw FatalBackendException("Couldn't create a local folder at " + folder.path)
			}
			return LocalStorageNodeFactory.folder(parentFolder, createFolder)
		} ?: throw ParentFolderIsNullException(folder.name)
	}

	@Throws(BackendException::class)
	fun move(source: LocalNode, target: LocalNode): LocalNode {
		target.parent?.let {
			val sourceFile = File(source.path)
			val targetFile = File(target.path)
			if (targetFile.exists()) {
				throw CloudNodeAlreadyExistsException(target.name)
			}
			if (!sourceFile.exists()) {
				throw NoSuchCloudFileException(source.name)
			}
			if (!sourceFile.renameTo(targetFile)) {
				throw FatalBackendException("Couldn't move " + source.path + " to " + target.path)
			}

			return LocalStorageNodeFactory.from(it, targetFile)
		} ?: throw ParentFolderIsNullException(target.name)
	}

	fun delete(node: CloudNode) {
		val fileOrDirectory = File(node.path)
		if (!deleteRecursive(fileOrDirectory)) {
			throw FatalBackendException("Couldn't delete local CloudNode $fileOrDirectory")
		}
	}

	private fun deleteRecursive(fileOrDirectory: File): Boolean {
		if (fileOrDirectory.isDirectory) {
			fileOrDirectory.listFiles()?.forEach {
				deleteRecursive(it)
			}
		}
		return fileOrDirectory.delete()
	}

	@Throws(IOException::class, BackendException::class)
	fun write(file: LocalFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): LocalFile {
		if (!replace && exists(file)) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}
		progressAware.onProgress(Progress.started(UploadState.upload(file)))
		val localFile = File(file.path)
		FileOutputStream(localFile).use { out ->
			data.open(context)?.use { inputStream ->
				object : TransferredBytesAwareInputStream(inputStream) {
					override fun bytesTransferred(transferred: Long) {
						progressAware.onProgress( //
							Progress.progress(UploadState.upload(file)) //
								.between(0) //
								.and(size) //
								.withValue(transferred)
						)
					}
				}.use { CopyStream.copyStreamToStream(it, out) }
			} ?: throw FatalBackendException("InputStream shouldn't be null")
		}
		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return LocalStorageNodeFactory.file( //
			file.parent,  //
			file.name,  //
			localFile.path,  //
			localFile.length(),  //
			Date(localFile.lastModified())
		)
	}

	@Throws(IOException::class)
	fun read(file: LocalFile, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		val localFile = File(file.path)
		FileInputStream(localFile).use { inputStream ->
			object : TransferredBytesAwareOutputStream(data) {
				override fun bytesTransferred(transferred: Long) {
					progressAware //
						.onProgress(
							Progress.progress(DownloadState.download(file)) //
								.between(0) //
								.and(localFile.length()) //
								.withValue(transferred)
						)
				}
			}.use { out -> CopyStream.copyStreamToStream(inputStream, out) }
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

}
