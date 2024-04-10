package org.cryptomator.data.cloud.onedrive

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
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.folder
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.from
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.getDriveId
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.getId
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.isFolder
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.OnedriveCloud
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
import org.cryptomator.util.Optional
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

internal class OnedriveImpl(private val cloud: OnedriveCloud, private val client: GraphServiceClient<Request>, private val idCache: OnedriveIdCache, private val context: Context) {

	private val sharedPreferencesHandler: SharedPreferencesHandler
	private var diskLruCache: DiskLruCache? = null

	private fun drive(driveId: String?): DriveRequestBuilder {
		return if (driveId == null) client.me().drive() else client.drives(driveId)
	}

	fun root(): OnedriveFolder {
		return RootOnedriveFolder(cloud)
	}

	fun resolve(path: String): OnedriveFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder = root()
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	fun file(parent: OnedriveFolder, name: String): OnedriveFile {
		return OnedriveCloudNodeFactory.file(parent, name, null)
	}

	fun file(parent: OnedriveFolder, name: String, size: Long?): OnedriveFile {
		return OnedriveCloudNodeFactory.file(parent, name, size)
	}

	fun folder(parent: OnedriveFolder, name: String): OnedriveFolder {
		return OnedriveCloudNodeFactory.folder(parent, name)
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
		return try {
			val responseCodeField = GraphServiceException::class.java.getDeclaredField("responseCode")
			responseCodeField.isAccessible = true
			val responseCode = responseCodeField[error] as Int
			responseCode == 404
		} catch (e: NoSuchFieldException) {
			throw IllegalStateException(e)
		} catch (e: IllegalAccessException) {
			throw IllegalStateException(e)
		}
	}

	fun exists(node: OnedriveNode): Boolean {
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
	fun list(folder: OnedriveFolder): List<OnedriveNode> {
		val result: MutableList<OnedriveNode> = ArrayList()
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
	fun create(folder: OnedriveFolder): OnedriveFolder {
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
	fun move(source: OnedriveNode, target: OnedriveNode): OnedriveNode {
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
	fun write(file: OnedriveFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): OnedriveFile {
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
			val lastModifiedDate = getLastModifiedDateTime(result.get().fileSystemInfo)
			OnedriveCloudNodeFactory.file(file.parent, result.get(), lastModifiedDate)
		} catch (e: ExecutionException) {
			throw FatalBackendException(e)
		} catch (e: InterruptedException) {
			throw FatalBackendException(e)
		}
	}

	private fun getLastModifiedDateTime(fileSystemInfo: FileSystemInfo?): Date {
		return fileSystemInfo?.lastModifiedDateTime.let { date ->
			Date.from(date?.toInstant())
		}?: Date.from(Date().toInstant())
	}

	@Throws(NoSuchCloudFileException::class)
	private fun uploadFile(file: OnedriveFile, data: DataSource, progressAware: ProgressAware<UploadState>, result: CompletableFuture<DriveItem>, conflictBehaviorOption: Option, size: Long) {
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
								if (error == null) {
									val diffItem = DriveItem()
									diffItem.fileSystemInfo = FileSystemInfo()
									setLastModifiedDateTime(diffItem.fileSystemInfo, data.modifiedDate(context))
									drive(parentNodeInfo.driveId) //
										.items(driveItem.id!!) //
										.buildRequest(conflictBehaviorOption) //
										.patchAsync(diffItem) //
										.whenComplete { driveItem, error ->
											if (error == null) {
												progressAware.onProgress(Progress.completed(UploadState.upload(file)))
												result.complete(driveItem)
												cacheNodeInfo(file, driveItem)
											} else {
												result.completeExceptionally(error)
											}
										}
								} else {
									result.completeExceptionally(error)
								}

							}
						}
				} catch (e: IOException) {
					throw FatalBackendException(e)
				}
			}
		} ?: throw FatalBackendException("InputStream shouldn't bee null")
	}

	@Throws(IOException::class, NoSuchCloudFileException::class)
	private fun chunkedUploadFile(file: OnedriveFile, data: DataSource, progressAware: ProgressAware<UploadState>, result: CompletableFuture<DriveItem>, conflictBehaviorOption: Option, size: Long) {
		val parentNodeInfo = requireNodeInfo(file.parent)

		val props = DriveItemUploadableProperties()
		props.fileSystemInfo = FileSystemInfo()
		setLastModifiedDateTime(props.fileSystemInfo, data.modifiedDate(context))

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

	private fun setLastModifiedDateTime(fileSystemInfo: FileSystemInfo?, modifiedDate: Optional<Date>) {
		fileSystemInfo?.lastModifiedDateTime = modifiedDate.map { date ->
			OffsetDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault())
		}.orElseGet {
			OffsetDateTime.ofInstant(Date().toInstant(), ZoneId.systemDefault())
		}
	}

	@Throws(BackendException::class, IOException::class)
	fun read(file: OnedriveFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
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
				Timber.tag("OnedriveImpl").w(e, "Error while retrieving content from Cache, get from web request")
				writeToData(file, nodeInfo, data, encryptedTmpFile, cacheKey, progressAware)
			}
		} else {
			writeToData(file, nodeInfo, data, encryptedTmpFile, cacheKey, progressAware)
		}
	}

	@Throws(IOException::class)
	private fun writeToData(file: OnedriveFile, nodeInfo: OnedriveIdCache.NodeInfo, data: OutputStream, encryptedTmpFile: File?, cacheKey: String?, progressAware: ProgressAware<DownloadState>) {
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
				} ?: Timber.tag("OnedriveImpl").e("Failed to store item in LRU cache")
			} catch (e: IOException) {
				Timber.tag("OnedriveImpl").e(e, "Failed to write downloaded file in LRU cache")
			}
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	private fun createLruCache(cacheSize: Int): Boolean {
		if (diskLruCache == null) {
			diskLruCache = try {
				DiskLruCache.create(LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.ONEDRIVE), cacheSize.toLong())
			} catch (e: IOException) {
				Timber.tag("OnedriveImpl").e(e, "Failed to setup LRU cache")
				return false
			}
		}
		return true
	}

	@Throws(NoSuchCloudFileException::class)
	fun delete(node: OnedriveNode) {
		val nodeInfo = requireNodeInfo(node)
		drive(nodeInfo.driveId).items(nodeInfo.id).buildRequest().delete()
		removeNodeInfo(node)
	}

	@Throws(NoSuchCloudFileException::class)
	private fun requireNodeInfo(node: OnedriveNode): OnedriveIdCache.NodeInfo {
		return nodeInfo(node) ?: throw NoSuchCloudFileException(node.path)
	}

	private fun nodeInfo(node: OnedriveNode): OnedriveIdCache.NodeInfo? {
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

	private fun <T : OnedriveNode> cacheNodeInfo(node: T, item: DriveItem): T {
		idCache.add(node.path, OnedriveIdCache.NodeInfo(getId(item), getDriveId(item), isFolder(item), item.cTag))
		return node
	}

	private fun removeNodeInfo(node: OnedriveNode) {
		idCache.remove(node.path)
	}

	private fun removeChildNodeInfo(folder: OnedriveFolder) {
		idCache.removeChildren(folder.path)
	}

	private fun loadNodeInfo(node: OnedriveNode): OnedriveIdCache.NodeInfo? {
		return if (node.parent == null) {
			loadRootNodeInfo()
		} else {
			loadNonRootNodeInfo(node)
		}
	}

	private fun loadRootNodeInfo(): OnedriveIdCache.NodeInfo {
		return drive(null).root().buildRequest().get()?.let { rootItem ->
			OnedriveIdCache.NodeInfo(getId(rootItem), getDriveId(rootItem), true, rootItem.cTag)
		} ?: throw FatalBackendException("Failed to load root item, item is null")
	}

	private fun loadNonRootNodeInfo(node: OnedriveNode): OnedriveIdCache.NodeInfo? {
		node.parent?.let { targetsParent ->
			val parentNodeInfo = nodeInfo(targetsParent)
			if (parentNodeInfo?.driveId == null) {
				return null
			}
			val item = childByName(parentNodeInfo.id, parentNodeInfo.driveId, node.name)
			return if (item == null) {
				null
			} else {
				OnedriveIdCache.NodeInfo(getId(item), getDriveId(item), isFolder(item), item.cTag)
			}
		} ?: throw ParentFolderIsNullException(node.name)
	}

	fun currentAccount(username: String): String {
		// used to check authentication
		client.me().drive().buildRequest().get()?.owner?.user
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
