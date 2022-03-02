package org.cryptomator.data.cloud.webdav

import android.content.Context
import org.cryptomator.data.cloud.webdav.network.ConnectionHandlerHandlerImpl
import org.cryptomator.data.cloud.webdav.network.ServerNotWebdavCompatibleException
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.WebDavCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NotFoundException
import org.cryptomator.domain.exception.ParentFolderDoesNotExistException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.exception.authentication.WebDavNotSupportedException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.IOException
import java.io.OutputStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal class WebDavImpl(private val cloud: WebDavCloud, private val connectionHandler: ConnectionHandlerHandlerImpl, private val context: Context) {

	private val baseUrl: HttpUrl = cloud.url().toHttpUrlOrNull() ?: throw FatalBackendException("Cloud url shouldn't be null")

	private val root: RootWebDavFolder = RootWebDavFolder(cloud)

	fun root(): WebDavFolder {
		return root
	}

	fun resolve(path: String): WebDavFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder: WebDavFolder = root
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	fun file(parent: WebDavFolder, name: String, size: Long?): WebDavFile {
		return WebDavFile(parent, name, parent.path + '/' + name, size, null)
	}

	fun folder(parent: WebDavFolder, name: String): WebDavFolder {
		return WebDavFolder(parent, name, parent.path + '/' + name)
	}

	@Throws(BackendException::class)
	fun exists(node: WebDavNode): Boolean {
		node.parent?.let {
			return try {
				connectionHandler.get(absoluteUriFrom(node.path), it) != null
			} catch (e: NotFoundException) {
				false
			}
		} ?: throw ParentFolderIsNullException(node.name)
	}

	@Throws(BackendException::class)
	fun list(folder: WebDavFolder): List<WebDavNode> {
		return connectionHandler.dirList(absoluteUriFrom(folder.path), folder)
	}

	@Throws(BackendException::class)
	fun create(folder: WebDavFolder): WebDavFolder {
		return try {
			createExcludingParents(folder)
		} catch (e: NotFoundException) {
			folder.parent?.let {
				create(it)
				createExcludingParents(folder)
			} ?: throw ParentFolderIsNullException(folder.name)
		} catch (e: ParentFolderDoesNotExistException) {
			folder.parent?.let {
				create(it)
				createExcludingParents(folder)
			} ?: throw ParentFolderIsNullException(folder.name)
		}
	}

	@Throws(BackendException::class)
	private fun createExcludingParents(folder: WebDavFolder): WebDavFolder {
		return if (folder.parent == null) {
			folder
		} else {
			connectionHandler.createFolder(absoluteUriFrom(folder.path), folder)
		}
	}

	@Throws(BackendException::class)
	fun move(source: WebDavFolder, target: WebDavFolder): WebDavFolder {
		moveFileOrFolder(source, target)
		return WebDavFolder(
			target.parent, target.name, target.path
		)
	}

	@Throws(BackendException::class)
	fun move(source: WebDavFile, target: WebDavFile): WebDavFile {
		moveFileOrFolder(source, target)
		return WebDavFile(target.parent, target.name, target.path, source.size, source.modified)
	}

	@Throws(BackendException::class)
	private fun moveFileOrFolder(source: WebDavNode, target: WebDavNode) {
		if (exists(target)) {
			throw CloudNodeAlreadyExistsException(target.name)
		}
		connectionHandler.move(absoluteUriFrom(source.path), absoluteUriFrom(target.path))
	}

	@Throws(BackendException::class, IOException::class)
	fun write(uploadFile: WebDavFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): WebDavFile {
		if (!replace && exists(uploadFile)) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}

		progressAware.onProgress(Progress.started(UploadState.upload(uploadFile)))
		data.open(context)?.use { inputStream ->
			object : TransferredBytesAwareInputStream(inputStream) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress( //
						Progress.progress(UploadState.upload(uploadFile)) //
							.between(0) //
							.and(size) //
							.withValue(transferred)
					)
				}
			}.use {
				connectionHandler.writeFile(absoluteUriFrom(uploadFile.path), it)
			}
		} ?: throw FatalBackendException("InputStream shouldn't bee null")

		return connectionHandler.get(absoluteUriFrom(uploadFile.path), uploadFile.parent) as WebDavFile? ?: throw FatalBackendException("Unable to get CloudFile after upload.")
	}

	@Throws(BackendException::class)
	fun checkAuthenticationAndServerCompatibility(url: String) {
		try {
			connectionHandler.checkAuthenticationAndServerCompatibility(url)
		} catch (ex: ServerNotWebdavCompatibleException) {
			throw WebDavNotSupportedException(cloud)
		}
	}

	@Throws(BackendException::class, IOException::class)
	fun read(file: CloudFile, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		connectionHandler.readFile(absoluteUriFrom(file.path)).use { inputStream ->
			object : TransferredBytesAwareOutputStream(data) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress( //
						Progress.progress(DownloadState.download(file)) //
							.between(0) //
							.and(file.size ?: Long.MAX_VALUE) //
							.withValue(transferred)
					)
				}
			}.use { out -> CopyStream.copyStreamToStream(inputStream, out) }
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	@Throws(BackendException::class)
	fun delete(node: CloudNode) {
		connectionHandler.delete(absoluteUriFrom(node.path))
	}

	private fun absoluteUriFrom(path: String): String {
		return baseUrl //
			.newBuilder() //
			.addPathSegments(path.removePrefix("/")) //
			.build() //
			.toString()
	}

	@Throws(BackendException::class)
	fun currentAccount(): String {
		checkAuthenticationAndServerCompatibility(cloud.url())
		return cloud.url()
	}

}
