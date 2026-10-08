package org.cryptomator.data.cloud.microsoftgraph

import android.content.Context
import com.microsoft.graph.http.GraphServiceException
import com.microsoft.graph.models.DriveItem
import com.microsoft.graph.models.DriveItemCreateUploadSessionParameterSet
import com.microsoft.graph.models.DriveItemUploadableProperties
import com.microsoft.graph.models.FileSystemInfo
import com.microsoft.graph.models.Folder
import com.microsoft.graph.models.ItemReference
import com.microsoft.graph.options.Option
import com.microsoft.graph.options.QueryOption
import com.microsoft.graph.requests.DriveRequestBuilder
import com.microsoft.graph.requests.GraphServiceClient
import com.microsoft.graph.tasks.LargeFileUploadTask
import com.tomclaw.cache.DiskLruCache
import org.cryptomator.data.cloud.microsoftgraph.MicrosoftGraphCloudNodeFactory.folder
import org.cryptomator.data.cloud.microsoftgraph.MicrosoftGraphCloudNodeFactory.from
import org.cryptomator.data.cloud.microsoftgraph.MicrosoftGraphCloudNodeFactory.getDriveId
import org.cryptomator.data.cloud.microsoftgraph.MicrosoftGraphCloudNodeFactory.getId
import org.cryptomator.data.cloud.microsoftgraph.MicrosoftGraphCloudNodeFactory.isFolder
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.MicrosoftGraphCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException
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
import java.io.OutputStream
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import okhttp3.Request
import timber.log.Timber

internal class MicrosoftGraphImpl(private val cloud: MicrosoftGraphCloud, private val client: GraphServiceClient<Request>, private val idCache: MicrosoftGraphIdCache, private val context: Context) {

	private val sharedPreferencesHandler: SharedPreferencesHandler
	private var diskLruCache: DiskLruCache? = null

	private fun drive(driveId: String?): DriveRequestBuilder {
		return if (driveId == null) client.me().drive() else client.drives(driveId)
	}

	fun root(): MicrosoftGraphFolder {
		return RootMicrosoftGraphFolder(cloud)
	}

	fun resolve(path: String): MicrosoftGraphFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder = root()
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	fun file(parent: MicrosoftGraphFolder, name: String): MicrosoftGraphFile {
		return MicrosoftGraphCloudNodeFactory.file(parent, name, null)
	}

	fun file(parent: MicrosoftGraphFolder, name: String, size: Long?): MicrosoftGraphFile {
		return MicrosoftGraphCloudNodeFactory.file(parent, name, size)
	}

	fun folder(parent: MicrosoftGraphFolder, name: String): MicrosoftGraphFolder {
		return MicrosoftGraphCloudNodeFactory.folder(parent, name)
	}

	private fun childByName(parentId: String, parentDriveId: String, name: String): DriveItem? {
		return try {
			drive(parentDriveId).items(parentId).itemWithPath(name).buildRequest().get()
		} catch (e: GraphServiceException) {
			if (isNotFoundError(e)) {
				null
			} else {
				throw e
			}
		}
	}

	private fun isNotFoundError(error: GraphServiceException): Boolean {
		return error.responseCode == 404
	}

	fun exists(node: MicrosoftGraphNode): Boolean {
		node.parent?.let {
			val parentNodeInfo = nodeInfo(it)
			if (parentNodeInfo?.driveId == null) {
				removeNodeInfo(node)
				return false
			}
			val item = childByName(parentNodeInfo.id, parentNodeInfo.driveId, node.name)
			if (item == null) {
				removeNodeInfo(node)
				return false
			}
			cacheNodeInfo(node, item)
			return true
		} ?: throw ParentFolderIsNullException(node.name)
	}

	@Throws(BackendException::class)
	fun list(folder: MicrosoftGraphFolder): List<MicrosoftGraphNode> {
		val result: MutableList<MicrosoftGraphNode> = ArrayList()
		val nodeInfo = requireNodeInfo(folder)
		var page = drive(nodeInfo.driveId).items(nodeInfo.id).children().buildRequest().get()
		do {
			removeChildNodeInfo(folder)
			page?.currentPage?.forEach {
				result.add(cacheNodeInfo(from(folder, it), it))
			}
			page = if (page?.nextPage != null) {
				page.nextPage?.buildRequest()?.get()
			} else {
				null
			}
		} while (page != null)
		return result
	}

	@Throws(NoSuchCloudFileException::class)
	fun create(folder: MicrosoftGraphFolder): MicrosoftGraphFolder {
		var parent = folder.parent
		parent?.let { parentFolder ->
			if (nodeInfo(parentFolder) == null) {
				parent = create(parentFolder)
			}
		} ?: throw ParentFolderIsNullException(folder.name)
		parent?.let { parentFolder ->
			val folderToCreate = DriveItem()
			folderToCreate.name = folder.name
			folderToCreate.folder = Folder()
			val parentNodeInfo = requireNodeInfo(parentFolder)
			val createdFolder = drive(parentNodeInfo.driveId).items(parentNodeInfo.id).children().buildRequest().post(folderToCreate)
			return cacheNodeInfo(folder(parentFolder, createdFolder), createdFolder)
		} ?: throw ParentFolderIsNullException(folder.name)
	}

	@Throws(NoSuchCloudFileException::class, CloudNodeAlreadyExistsException::class)
	fun move(source: MicrosoftGraphNode, target: MicrosoftGraphNode): MicrosoftGraphNode {
		target.parent?.let { targetsParent ->
			if (exists(target)) {
				throw CloudNodeAlreadyExistsException(target.name)
			}
			val targetItem = DriveItem()
			targetItem.name = target.name
			val targetParentReference = ItemReference()
			val targetNodeInfo = nodeInfo(targetsParent)
			targetParentReference.id = targetNodeInfo?.id
			targetParentReference.driveId = targetNodeInfo?.driveId
			targetItem.parentReference = targetParentReference
			val sourceNodeInfo = requireNodeInfo(source)
			drive(sourceNodeInfo.driveId).items(sourceNodeInfo.id).buildRequest().patch(targetItem)?.let {
				removeNodeInfo(source)
				return cacheNodeInfo(from(targetsParent, it), it)
			} ?: throw FatalBackendException("Failed to move file, response is null")
		} ?: throw ParentFolderIsNullException(target.name)
	}

	@Throws(BackendException::class)
	fun write(file: MicrosoftGraphFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): MicrosoftGraphFile {
		if (!replace && exists(file)) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}
		progressAware.onProgress(Progress.started(UploadState.upload(file)))
		var uploadMode = NON_REPLACING_MODE
		if (replace) {
			uploadMode = REPLACE_MODE
		}
		val conflictBehaviorOption: Option = QueryOption("@name.conflictBehavior", uploadMode)
		val result = CompletableFuture<DriveItem>()
		if (size <= CHUNKED_UPLOAD_MAX_SIZE) {
			uploadFile(file, data, progressAware, result, conflictBehaviorOption, size)
		} else {
			try {
				chunkedUploadFile(file, data, progressAware, result, conflictBehaviorOption, size)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}
		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return try {
			val driveItem: DriveItem = result.get()
			val lastModifiedDate = getLastModifiedDateTime(driveItem) ?: Date()
			MicrosoftGraphCloudNodeFactory.file(file.parent, driveItem, lastModifiedDate)
		} catch (e: ExecutionException) {
			throw FatalBackendException(e)
		} catch (e: InterruptedException) {
			throw FatalBackendException(e)
		}
	}

	private fun getLastModifiedDateTime(driveItem: DriveItem): Date? {
		return driveItem.fileSystemInfo?.lastModifiedDateTime?.let { clientDate -> Date.from(clientDate.toInstant()) }
			?: driveItem.lastModifiedDateTime?.let { serverDate -> Date.from(serverDate.toInstant()) }
	}

	@Throws(NoSuchCloudFileException::class)
	private fun uploadFile(file: MicrosoftGraphFile, data: DataSource, progressAware: ProgressAware<UploadState>, result: CompletableFuture<DriveItem>, conflictBehaviorOption: Option, size: Long) {
		data.open(context)?.use { inputStream ->
			object : TransferredBytesAwareInputStream(inputStream) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress(Progress.progress(UploadState.upload(file)).between(0).and(size).withValue(transferred))
				}
			}.use {
				val parentNodeInfo = requireNodeInfo(file.parent)
				try {
					drive(parentNodeInfo.driveId) //
						.items(parentNodeInfo.id) //
						.itemWithPath(file.name) //
						.content() //
						.buildRequest(listOf(conflictBehaviorOption)) //
						.putAsync(CopyStream.toByteArray(it)) //
						.whenComplete { driveItem, error ->
							run {
								val modifiedDate = data.modifiedDate(context)
								if (error != null) {
									result.completeExceptionally(error)
									return@whenComplete
								}
								if (modifiedDate.isPresent) {
									patchAsyncLastModifiedDate(parentNodeInfo, driveItem, modifiedDate.get())
										.whenComplete { driveItem, error ->
											if (error == null) {
												progressAware.onProgress(Progress.completed(UploadState.upload(file)))
												result.complete(driveItem)
												cacheNodeInfo(file, driveItem)
											} else {
												result.completeExceptionally(error)
											}
										}
								} else { // current date is the default, no need to patch()
									progressAware.onProgress(Progress.completed(UploadState.upload(file)))
									result.complete(driveItem)
									cacheNodeInfo(file, driveItem)
								}
							}
						}
				} catch (e: IOException) {
					throw FatalBackendException(e)
				}
			}
		} ?: throw FatalBackendException("InputStream shouldn't bee null")
	}

	private fun patchAsyncLastModifiedDate(parentNodeInfo: MicrosoftGraphIdCache.NodeInfo, driveItem: DriveItem, modifiedDate: Date): CompletableFuture<DriveItem> {
		val diffItem = DriveItem()
		diffItem.fileSystemInfo = FileSystemInfo()
		diffItem.fileSystemInfo!!.lastModifiedDateTime = OffsetDateTime.ofInstant(modifiedDate.toInstant(), ZoneId.systemDefault())
		return drive(parentNodeInfo.driveId) //
			.items(driveItem.id!!) //
			.buildRequest() //
			.patchAsync(diffItem) //
	}

	@Throws(IOException::class, NoSuchCloudFileException::class)
	private fun chunkedUploadFile(file: MicrosoftGraphFile, data: DataSource, progressAware: ProgressAware<UploadState>, result: CompletableFuture<DriveItem>, conflictBehaviorOption: Option, size: Long) {
		val parentNodeInfo = requireNodeInfo(file.parent)

		val props = DriveItemUploadableProperties()
		val modifiedDate = data.modifiedDate(context)

		if (modifiedDate.isPresent) {
			props.fileSystemInfo = FileSystemInfo()
			props.fileSystemInfo!!.lastModifiedDateTime = OffsetDateTime.ofInstant(modifiedDate.get().toInstant(), ZoneId.systemDefault())
		}

		drive(parentNodeInfo.driveId) //
			.items(parentNodeInfo.id) //
			.itemWithPath(file.name) //
			.createUploadSession(DriveItemCreateUploadSessionParameterSet.newBuilder().withItem(props).build()) //
			.buildRequest() //
			.post()?.let { uploadSession ->
				data.open(context)?.use { inputStream ->
					LargeFileUploadTask(uploadSession, client, inputStream, size, DriveItem::class.java) //
						.uploadAsync(CHUNKED_UPLOAD_CHUNK_SIZE, listOf(conflictBehaviorOption)) { current, max ->
							progressAware.onProgress(
								Progress.progress(UploadState.upload(file)).between(0).and(max).withValue(current)
							)
						}.whenComplete { driveItemResult, error ->
							run {
								if (error == null && driveItemResult.responseBody != null) {
									progressAware.onProgress(Progress.completed(UploadState.upload(file)))
									result.complete(driveItemResult.responseBody)
									cacheNodeInfo(file, driveItemResult.responseBody!!)
								} else {
									result.completeExceptionally(error)
								}
							}
						}
				} ?: throw FatalBackendException("InputStream shouldn't bee null")
			} ?: throw FatalBackendException("Failed to create upload session, response is null")
	}

	@Throws(BackendException::class, IOException::class)
	fun read(file: MicrosoftGraphFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		var cacheKey: String? = null
		var cacheFile: File? = null
		val nodeInfo = requireNodeInfo(file)
		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			cacheKey = nodeInfo.id + nodeInfo.getcTag()
			cacheFile = diskLruCache?.let { it[cacheKey] }
		}
		if (sharedPreferencesHandler.useLruCache() && cacheFile != null) {
			try {
				retrieveFromLruCache(cacheFile, data)
			} catch (e: IOException) {
				Timber.tag("MicrosoftGraphImpl").w(e, "Error while retrieving content from Cache, get from web request")
				writeToData(file, nodeInfo, data, encryptedTmpFile, cacheKey, progressAware)
			}
		} else {
			writeToData(file, nodeInfo, data, encryptedTmpFile, cacheKey, progressAware)
		}
	}

	@Throws(IOException::class)
	private fun writeToData(file: MicrosoftGraphFile, nodeInfo: MicrosoftGraphIdCache.NodeInfo, data: OutputStream, encryptedTmpFile: File?, cacheKey: String?, progressAware: ProgressAware<DownloadState>) {
		val request = drive(nodeInfo.driveId).items(nodeInfo.id).content().buildRequest()
		request.get()?.use { inputStream ->
			object : TransferredBytesAwareOutputStream(data) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress(Progress.progress(DownloadState.download(file)).between(0).and(file.size ?: Long.MAX_VALUE).withValue(transferred))
				}
			}.use { out -> CopyStream.copyStreamToStream(inputStream, out) }
		}
		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile != null && cacheKey != null) {
			try {
				diskLruCache?.let {
					LruFileCacheUtil.storeToLruCache(it, cacheKey, encryptedTmpFile)
				} ?: Timber.tag("MicrosoftGraphImpl").e("Failed to store item in LRU cache")
			} catch (e: IOException) {
				Timber.tag("MicrosoftGraphImpl").e(e, "Failed to write downloaded file in LRU cache")
			}
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	private fun createLruCache(cacheSize: Int): Boolean {
		if (diskLruCache == null) {
			diskLruCache = try {
				DiskLruCache.create(LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.MICROSOFT_GRAPH), cacheSize.toLong())
			} catch (e: IOException) {
				Timber.tag("MicrosoftGraphImpl").e(e, "Failed to setup LRU cache")
				return false
			}
		}
		return true
	}

	@Throws(NoSuchCloudFileException::class)
	fun delete(node: MicrosoftGraphNode) {
		val nodeInfo = requireNodeInfo(node)
		drive(nodeInfo.driveId).items(nodeInfo.id).buildRequest().delete()
		removeNodeInfo(node)
	}

	@Throws(NoSuchCloudFileException::class)
	private fun requireNodeInfo(node: MicrosoftGraphNode): MicrosoftGraphIdCache.NodeInfo {
		return nodeInfo(node) ?: throw NoSuchCloudFileException(node.path)
	}

	private fun nodeInfo(node: MicrosoftGraphNode): MicrosoftGraphIdCache.NodeInfo? {
		var result = idCache[node.path]
		if (result == null) {
			result = loadNodeInfo(node)
			if (result == null) {
				return null
			} else {
				idCache.add(node.path, result)
			}
		}
		return if (result.isFolder != node.isFolder) {
			null
		} else result
	}

	private fun <T : MicrosoftGraphNode> cacheNodeInfo(node: T, item: DriveItem): T {
		idCache.add(node.path, MicrosoftGraphIdCache.NodeInfo(getId(item), getDriveId(item), isFolder(item), item.cTag))
		return node
	}

	private fun removeNodeInfo(node: MicrosoftGraphNode) {
		idCache.remove(node.path)
	}

	private fun removeChildNodeInfo(folder: MicrosoftGraphFolder) {
		idCache.removeChildren(folder.path)
	}

	private fun loadNodeInfo(node: MicrosoftGraphNode): MicrosoftGraphIdCache.NodeInfo? {
		return if (node.parent == null) {
			loadRootNodeInfo()
		} else {
			loadNonRootNodeInfo(node)
		}
	}

	private fun loadRootNodeInfo(): MicrosoftGraphIdCache.NodeInfo {
		return drive(cloud.driveId()).root().buildRequest().get()?.let { rootItem ->
			MicrosoftGraphIdCache.NodeInfo(getId(rootItem), getDriveId(rootItem), true, rootItem.cTag)
		} ?: throw FatalBackendException("Failed to load root item, item is null")
	}

	private fun loadNonRootNodeInfo(node: MicrosoftGraphNode): MicrosoftGraphIdCache.NodeInfo? {
		node.parent?.let { targetsParent ->
			val parentNodeInfo = nodeInfo(targetsParent)
			if (parentNodeInfo?.driveId == null) {
				return null
			}
			val item = childByName(parentNodeInfo.id, parentNodeInfo.driveId, node.name)
			return if (item == null) {
				null
			} else {
				MicrosoftGraphIdCache.NodeInfo(getId(item), getDriveId(item), isFolder(item), item.cTag)
			}
		} ?: throw ParentFolderIsNullException(node.name)
	}

	fun currentAccount(username: String): String {
		// used to check authentication
		drive(cloud.driveId()).buildRequest().get()
		return username
	}

	fun logout() {
		// FIXME what about logout?
	}

	companion object {

		private const val CHUNKED_UPLOAD_MAX_SIZE = 4L shl 20
		private const val CHUNKED_UPLOAD_CHUNK_SIZE = 327680 * 32
		private const val REPLACE_MODE = "replace"
		private const val NON_REPLACING_MODE = "rename"
	}

	init {
		if (cloud.accessToken() == null) {
			throw NoAuthenticationProvidedException(cloud)
		}
		sharedPreferencesHandler = SharedPreferencesHandler(context)
	}
}
