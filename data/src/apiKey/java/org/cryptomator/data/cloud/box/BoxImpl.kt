package org.cryptomator.data.cloud.box

import android.content.Context
import com.tomclaw.cache.DiskLruCache
import org.cryptomator.data.cloud.webdav.network.DataSourceBasedRequestBody
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.BoxCloud
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
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.file.LruFileCacheUtil
import org.cryptomator.util.file.LruFileCacheUtil.Companion.retrieveFromLruCache
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import timber.log.Timber

internal class BoxImpl(private val cloud: BoxCloud, private val api: BoxApi, private val idCache: BoxIdCache, private val context: Context) {

	private val sharedPreferencesHandler: SharedPreferencesHandler by lazy { SharedPreferencesHandler(context) }
	private var diskLruCache: DiskLruCache? = null

	fun root(): BoxFolder {
		return RootBoxFolder(cloud)
	}

	fun resolve(path: String): BoxFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder = root()
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	fun file(parent: BoxFolder, name: String, size: Long?): BoxFile {
		return BoxCloudNodeFactory.file(parent, name, size)
	}

	fun folder(parent: BoxFolder, name: String): BoxFolder {
		return BoxCloudNodeFactory.folder(parent, name)
	}

	@Throws(IOException::class)
	fun exists(node: BoxNode): Boolean {
		return currentNodeInfo(node) != null
	}

	@Throws(IOException::class, BackendException::class)
	fun list(folder: BoxFolder): List<BoxNode> {
		val folderInfo = requireNodeInfo(folder)
		val vanishedChildren = idCache.childPaths(folder.path).toMutableSet()
		val result: MutableList<BoxNode> = ArrayList()
		forEachChild(folderInfo.id) { item ->
			if (item.isFolder || item.isFile) {
				val node = cacheNodeInfo(BoxCloudNodeFactory.from(folder, item), item)
				vanishedChildren.remove(node.path)
				result.add(node)
			}
			false
		}
		vanishedChildren.forEach { idCache.remove(it) }
		return result
	}

	@Throws(IOException::class, BackendException::class)
	fun create(folder: BoxFolder): BoxFolder {
		val parent = folder.parent ?: throw ParentFolderIsNullException(folder.name)
		val parentInfo = nodeInfo(parent) ?: requireNodeInfo(create(parent))
		val createdFolder = api.createFolder(parentInfo.id, folder.name)
		return cacheNodeInfo(BoxCloudNodeFactory.folder(parent, createdFolder), createdFolder)
	}

	@Throws(IOException::class, BackendException::class)
	fun move(source: BoxNode, target: BoxNode): BoxNode {
		val targetParent = target.parent ?: throw ParentFolderIsNullException(target.name)
		if (exists(target)) {
			throw CloudNodeAlreadyExistsException(target.name)
		}
		val movedItem = api.move(requireNodeInfo(source), requireNodeInfo(targetParent).id, target.name)
		idCache.remove(source.path)
		return cacheNodeInfo(BoxCloudNodeFactory.from(targetParent, movedItem), movedItem)
	}

	@Throws(IOException::class, BackendException::class)
	fun write(file: BoxFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): BoxFile {
		val existingFile = currentNodeInfo(file)
		if (existingFile != null && !replace) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}
		val parentInfo = requireNodeInfo(file.parent)
		val modified = data.modifiedDate(context).orElse(null)
		progressAware.onProgress(Progress.started(UploadState.upload(file)))
		val uploadedFile = if (size < CHUNKED_UPLOAD_THRESHOLD) {
			uploadFile(file, existingFile, parentInfo, data, progressAware, size, modified)
		} else {
			chunkedUploadFile(file, existingFile, parentInfo, data, progressAware, size, modified)
		}
		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return cacheNodeInfo(BoxCloudNodeFactory.file(file.parent, uploadedFile), uploadedFile)
	}

	private fun uploadFile(
		file: BoxFile,
		existingFile: BoxIdCache.NodeInfo?,
		parentInfo: BoxIdCache.NodeInfo,
		data: DataSource,
		progressAware: ProgressAware<UploadState>,
		size: Long,
		modified: Date?
	): BoxItem {
		val content = DataSourceBasedRequestBody.from(context, data, size) { inputStream: InputStream ->
			object : TransferredBytesAwareInputStream(inputStream) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress(Progress.progress(UploadState.upload(file)).between(0).and(size).withValue(transferred))
				}
			}
		}
		return existingFile?.let { api.uploadVersion(it.id, file.name, modified, content) } //
			?: api.upload(parentInfo.id, file.name, modified, content)
	}

	/**
	 * Box rejects files of 50 MiB and more in a single request, so these are uploaded in parts of the size the upload session demands.
	 */
	private fun chunkedUploadFile(
		file: BoxFile,
		existingFile: BoxIdCache.NodeInfo?,
		parentInfo: BoxIdCache.NodeInfo,
		data: DataSource,
		progressAware: ProgressAware<UploadState>,
		size: Long,
		modified: Date?
	): BoxItem {
		val session = existingFile?.let { api.createVersionUploadSession(it.id, file.name, size) } //
			?: api.createUploadSession(parentInfo.id, file.name, size)
		try {
			val fileDigest = BoxDigest.sha1()
			val parts: MutableList<BoxUploadPart> = ArrayList()
			val buffer = ByteArray(session.partSize.toInt())
			data.open(context)?.use { inputStream ->
				var offset = 0L
				while (offset < size) {
					val length = readPart(inputStream, buffer, minOf(session.partSize, size - offset).toInt())
					fileDigest.update(buffer, 0, length)
					parts.add(api.uploadPart(session, buffer, length, offset, size))
					offset += length
					progressAware.onProgress(Progress.progress(UploadState.upload(file)).between(0).and(size).withValue(offset))
				}
			} ?: throw FatalBackendException("InputStream shouldn't be null")
			return api.commitUploadSession(session, parts, BoxDigest.base64(fileDigest), modified)
		} catch (e: Exception) {
			abortQuietly(session)
			throw e
		}
	}

	private fun abortQuietly(session: BoxUploadSession) {
		try {
			api.abortUploadSession(session)
		} catch (e: Exception) {
			Timber.tag("BoxImpl").w(e, "Failed to abort upload session")
		}
	}

	private fun readPart(inputStream: InputStream, buffer: ByteArray, length: Int): Int {
		var read = 0
		while (read < length) {
			val count = inputStream.read(buffer, read, length - read)
			if (count == -1) {
				throw FatalBackendException("Data ended before reaching the announced size")
			}
			read += count
		}
		return read
	}

	@Throws(IOException::class, BackendException::class)
	fun read(file: BoxFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		val nodeInfo = requireNodeInfo(file)
		var cacheKey: String? = null
		var cacheFile: File? = null
		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			// asks for the current sha1 as the file might have been changed elsewhere, which would serve outdated content from the LRU cache
			cacheKey = api.fileInfo(nodeInfo.id).sha1?.let { nodeInfo.id + it }
			cacheFile = cacheKey?.let { key -> diskLruCache?.let { it[key] } }
		}
		if (cacheFile != null) {
			try {
				retrieveFromLruCache(cacheFile, data)
			} catch (e: IOException) {
				Timber.tag("BoxImpl").w(e, "Error while retrieving content from Cache, get from web request")
				writeToData(file, nodeInfo, data, encryptedTmpFile, cacheKey, progressAware)
			}
		} else {
			writeToData(file, nodeInfo, data, encryptedTmpFile, cacheKey, progressAware)
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	@Throws(IOException::class)
	private fun writeToData(file: BoxFile, nodeInfo: BoxIdCache.NodeInfo, data: OutputStream, encryptedTmpFile: File?, cacheKey: String?, progressAware: ProgressAware<DownloadState>) {
		api.download(nodeInfo.id).use { response ->
			val inputStream = response.body?.byteStream() ?: throw FatalBackendException("InputStream shouldn't be null")
			object : TransferredBytesAwareOutputStream(data) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress(Progress.progress(DownloadState.download(file)).between(0).and(file.size ?: Long.MAX_VALUE).withValue(transferred))
				}
			}.use { out -> CopyStream.copyStreamToStream(inputStream, out) }
		}
		if (encryptedTmpFile != null && cacheKey != null) {
			try {
				diskLruCache?.let {
					LruFileCacheUtil.storeToLruCache(it, cacheKey, encryptedTmpFile)
				} ?: Timber.tag("BoxImpl").e("Failed to store item in LRU cache")
			} catch (e: IOException) {
				Timber.tag("BoxImpl").e(e, "Failed to write downloaded file in LRU cache")
			}
		}
	}

	private fun createLruCache(cacheSize: Int): Boolean {
		if (diskLruCache == null) {
			diskLruCache = try {
				DiskLruCache.create(LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.BOX), cacheSize.toLong())
			} catch (e: IOException) {
				Timber.tag("BoxImpl").e(e, "Failed to setup LRU cache")
				return false
			}
		}
		return true
	}

	@Throws(IOException::class, BackendException::class)
	fun delete(node: BoxNode) {
		api.delete(requireNodeInfo(node))
		idCache.remove(node.path)
	}

	@Throws(IOException::class)
	fun currentAccount(): String {
		return api.currentUser().login ?: throw FatalBackendException("Box account has no login")
	}

	fun logout() {
		// connections are removed instead of being logged out
	}

	@Throws(IOException::class, NoSuchCloudFileException::class)
	private fun requireNodeInfo(node: BoxNode): BoxIdCache.NodeInfo {
		return nodeInfo(node) ?: throw NoSuchCloudFileException(node.path)
	}

	@Throws(IOException::class)
	private fun nodeInfo(node: BoxNode): BoxIdCache.NodeInfo? {
		val info = idCache[node.path] ?: loadNodeInfo(node) ?: return null
		return if (info.isFolder == node.isFolder) info else null
	}

	/**
	 * Like [nodeInfo] but asks Box instead of trusting the cache, which might be outdated. Resolves the parent again if its cached id
	 * belongs to a folder that doesn't exist anymore.
	 */
	@Throws(IOException::class)
	private fun currentNodeInfo(node: BoxNode): BoxIdCache.NodeInfo? {
		val parent = node.parent ?: return ROOT_NODE_INFO
		val info = try {
			lookUp(node, parent)
		} catch (e: BoxApiException) {
			if (!e.isNotFound) {
				throw e
			}
			idCache.remove(parent.path)
			lookUp(node, parent)
		}
		if (info == null) {
			idCache.remove(node.path)
		} else {
			idCache.add(node.path, info)
		}
		return info?.takeIf { it.isFolder == node.isFolder }
	}

	/**
	 * Looks through the parent folder only if Box denies the single-request lookup, e.g. in folders the user may not upload to.
	 */
	@Throws(IOException::class)
	private fun lookUp(node: BoxNode, parent: BoxFolder): BoxIdCache.NodeInfo? {
		val parentInfo = nodeInfo(parent) ?: return null
		return try {
			api.itemNamed(parentInfo.id, node.name)?.takeIf { it.name == node.name && (it.isFolder || it.isFile) }?.let { BoxIdCache.NodeInfo(it) }
		} catch (e: BoxApiException) {
			if (e.isAuthenticationError || e.isNotFound) {
				throw e
			}
			loadNodeInfo(node)
		}
	}

	/**
	 * Looks the node up in its parent folder, caching the siblings passed on the way.
	 */
	@Throws(IOException::class)
	private fun loadNodeInfo(node: BoxNode): BoxIdCache.NodeInfo? {
		val parent = node.parent ?: return ROOT_NODE_INFO
		val parentInfo = nodeInfo(parent) ?: return null
		var result: BoxIdCache.NodeInfo? = null
		forEachChild(parentInfo.id) { item ->
			val name = item.name
			if (name != null && (item.isFolder || item.isFile)) {
				val info = BoxIdCache.NodeInfo(item)
				idCache.add(parent.path + "/" + name, info)
				if (name == node.name) {
					result = info
				}
			}
			result != null
		}
		return result
	}

	/**
	 * Iterates over the pages of the folder's items until [action] returns `true`.
	 */
	@Throws(IOException::class)
	private fun forEachChild(folderId: String, action: (BoxItem) -> Boolean) {
		var marker: String? = null
		do {
			val page = api.folderItems(folderId, marker)
			page.entries?.forEach { item ->
				if (action(item)) {
					return
				}
			}
			marker = page.nextMarker?.takeIf { it.isNotEmpty() }
		} while (marker != null)
	}

	private fun <T : BoxNode> cacheNodeInfo(node: T, item: BoxItem): T {
		idCache.add(node.path, BoxIdCache.NodeInfo(item))
		return node
	}

	companion object {

		private const val CHUNKED_UPLOAD_THRESHOLD = 50L shl 20
		private val ROOT_NODE_INFO = BoxIdCache.NodeInfo(BoxApi.ROOT_FOLDER_ID, true)
	}
}
