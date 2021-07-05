package org.cryptomator.data.cloud.onedrive

import android.content.Context
import android.net.Uri
import com.microsoft.graph.concurrency.ChunkedUploadProvider
import com.microsoft.graph.http.GraphServiceException
import com.microsoft.graph.models.extensions.DriveItem
import com.microsoft.graph.models.extensions.DriveItemUploadableProperties
import com.microsoft.graph.models.extensions.Folder
import com.microsoft.graph.models.extensions.IGraphServiceClient
import com.microsoft.graph.models.extensions.ItemReference
import com.microsoft.graph.options.Option
import com.microsoft.graph.options.QueryOption
import com.microsoft.graph.requests.extensions.IDriveRequestBuilder
import com.tomclaw.cache.DiskLruCache
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.folder
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.from
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.getDriveId
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.getId
import org.cryptomator.data.cloud.onedrive.OnedriveCloudNodeFactory.isFolder
import org.cryptomator.data.cloud.onedrive.graph.ClientException
import org.cryptomator.data.cloud.onedrive.graph.ICallback
import org.cryptomator.data.cloud.onedrive.graph.IProgressCallback
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.OnedriveCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.ParentFolderDoesNotExistException
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
import java.util.ArrayList
import java.util.Date
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import timber.log.Timber

internal class OnedriveImpl(cloud: OnedriveCloud, context: Context, nodeInfoCache: OnedriveIdCache) {

	private val cloud: OnedriveCloud
	private val context: Context
	private val nodeInfoCache: OnedriveIdCache
	private val sharedPreferencesHandler: SharedPreferencesHandler
	private var diskLruCache: DiskLruCache? = null

	private fun client(): IGraphServiceClient {
		return OnedriveClientFactory.getInstance(context, cloud.accessToken())
	}

	private fun drive(driveId: String?): IDriveRequestBuilder {
		return if (driveId == null) client().me().drive() else client().drives(driveId)
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
			drive(parentDriveId) //
				.items(parentId) //
				.itemWithPath(Uri.encode(name)) //
				.buildRequest() //
				.get()
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
			if (parentNodeInfo == null) {
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
		var page = drive(nodeInfo.driveId) //
			.items(nodeInfo.id) //
			.children() //
			.buildRequest() //
			.get()
		do {
			removeChildNodeInfo(folder)
			page.currentPage?.forEach {
				result.add(cacheNodeInfo(from(folder, it), it))
			}
			page = if (page.nextPage != null) {
				page.nextPage.buildRequest().get()
			} else {
				null
			}
		} while (page != null)
		return result
	}

	@Throws(NoSuchCloudFileException::class)
	fun create(folder: OnedriveFolder): OnedriveFolder {
		var parent = folder.parent
		if (nodeInfo(parent!!) == null) { //FIXME
			if (parent == null) {
				throw ParentFolderDoesNotExistException()
			} else {
				parent = create(parent)
			}
		}
		val folderToCreate = DriveItem()
		folderToCreate.name = folder.name
		folderToCreate.folder = Folder()
		val parentNodeInfo = requireNodeInfo(parent)
		val createdFolder = drive(parentNodeInfo.driveId) //
			.items(parentNodeInfo.id).children() //
			.buildRequest() //
			.post(folderToCreate)
		return cacheNodeInfo(folder(parent, createdFolder), createdFolder)
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
			val movedItem = drive(sourceNodeInfo.driveId) //
				.items(sourceNodeInfo.id) //
				.buildRequest() //
				.patch(targetItem)
			removeNodeInfo(source)
			return cacheNodeInfo(from(targetsParent, movedItem), movedItem)
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
			uploadFile(file, data, progressAware, result, conflictBehaviorOption)
		} else {
			try {
				chunkedUploadFile(file, data, progressAware, result, conflictBehaviorOption, size)
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		}
		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return try {
			OnedriveCloudNodeFactory.file(file.parent, result.get(), Date())
		} catch (e: ExecutionException) {
			throw FatalBackendException(e)
		} catch (e: InterruptedException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(NoSuchCloudFileException::class)
	private fun uploadFile( //
		file: OnedriveFile,  //
		data: DataSource,  //
		progressAware: ProgressAware<UploadState>,  //
		result: CompletableFuture<DriveItem>,  //
		conflictBehaviorOption: Option
	) {
		val parentNodeInfo = requireNodeInfo(file.parent)
		try {
			data.open(context)?.use { inputStream ->
				drive(parentNodeInfo.driveId) //
					.items(parentNodeInfo.id) //
					.itemWithPath(file.name) //
					.content() //
					.buildRequest(listOf(conflictBehaviorOption)) //
					.put(CopyStream.toByteArray(inputStream), object : IProgressCallback<DriveItem> {
						override fun progress(current: Long, max: Long) {
							progressAware //
								.onProgress(
									Progress.progress(UploadState.upload(file)) //
										.between(0) //
										.and(max) //
										.withValue(current)
								)
						}

						override fun success(item: DriveItem) {
							progressAware.onProgress(Progress.completed(UploadState.upload(file)))
							result.complete(item)
							cacheNodeInfo(file, item)
						}

						override fun failure(ex: com.microsoft.graph.core.ClientException) {
							result.completeExceptionally(ex)
						}
					})
			} ?: throw FatalBackendException("InputStream shouldn't be null")
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(IOException::class, NoSuchCloudFileException::class)
	private fun chunkedUploadFile( //
		file: OnedriveFile,  //
		data: DataSource,  //
		progressAware: ProgressAware<UploadState>,  //
		result: CompletableFuture<DriveItem>,  //
		conflictBehaviorOption: Option,  //
		size: Long
	) {
		val parentNodeInfo = requireNodeInfo(file.parent)
		val uploadSession = drive(parentNodeInfo.driveId) //
			.items(parentNodeInfo.id) //
			.itemWithPath(file.name) //
			.createUploadSession(DriveItemUploadableProperties()) //
			.buildRequest() //
			.post()
		data.open(context).use { inputStream ->
			ChunkedUploadProvider(uploadSession, client(), inputStream, size, DriveItem::class.java) //
				.upload(listOf(conflictBehaviorOption), object : IProgressCallback<DriveItem> {
					override fun progress(current: Long, max: Long) {
						progressAware.onProgress(
							Progress //
								.progress(UploadState.upload(file)) //
								.between(0) //
								.and(max) //
								.withValue(current)
						)
					}

					override fun success(item: DriveItem) {
						progressAware.onProgress(Progress.completed(UploadState.upload(file)))
						result.complete(item)
						cacheNodeInfo(file, item)
					}

					override fun failure(ex: com.microsoft.graph.core.ClientException) {
						result.completeExceptionally(ex)
					}
				}, CHUNKED_UPLOAD_CHUNK_SIZE, CHUNKED_UPLOAD_MAX_ATTEMPTS)
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
	private fun writeToData(
		file: OnedriveFile,  //
		nodeInfo: OnedriveIdCache.NodeInfo,  //
		data: OutputStream,  //
		encryptedTmpFile: File?,  //
		cacheKey: String?,  //
		progressAware: ProgressAware<DownloadState>
	) {
		val request = drive(nodeInfo.driveId) //
			.items(nodeInfo.id) //
			.content() //
			.buildRequest()
		request.get().use { inputStream ->
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
		drive(nodeInfo.driveId) //
			.items(nodeInfo.id) //
			.buildRequest() //
			.delete()
		removeNodeInfo(node)
	}

	@Throws(NoSuchCloudFileException::class)
	private fun requireNodeInfo(node: OnedriveNode): OnedriveIdCache.NodeInfo {
		return nodeInfo(node) ?: throw NoSuchCloudFileException(node.path)
	}

	private fun nodeInfo(node: OnedriveNode): OnedriveIdCache.NodeInfo? {
		var result: OnedriveIdCache.NodeInfo? = nodeInfoCache[node.path]
		if (result == null) {
			result = loadNodeInfo(node)
			if (result == null) {
				return null
			} else {
				nodeInfoCache.add(node.path, result)
			}
		}
		return if (result.isFolder != node.isFolder) {
			null
		} else result
	}

	private fun <T : OnedriveNode?> cacheNodeInfo(node: T, item: DriveItem): T {
		nodeInfoCache.add( //
			node?.path!!, OnedriveIdCache.NodeInfo( //
				getId(item),  //
				getDriveId(item),  //
				isFolder(item),  //
				item.cTag //
			) //
		)
		return node
	}

	private fun removeNodeInfo(node: OnedriveNode) {
		nodeInfoCache.remove(node.path)
	}

	private fun removeChildNodeInfo(folder: OnedriveFolder) {
		nodeInfoCache.removeChildren(folder.path)
	}

	private fun loadNodeInfo(node: OnedriveNode): OnedriveIdCache.NodeInfo? {
		return if (node.parent == null) {
			loadRootNodeInfo()
		} else {
			loadNonRootNodeInfo(node)
		}
	}

	private fun loadRootNodeInfo(): OnedriveIdCache.NodeInfo {
		val item = drive(null).root().buildRequest().get()
		return OnedriveIdCache.NodeInfo(getId(item), getDriveId(item), true, item.cTag)
	}

	private fun loadNonRootNodeInfo(node: OnedriveNode): OnedriveIdCache.NodeInfo? {
		node.parent?.let { targetsParent ->
			val parentNodeInfo = nodeInfo(targetsParent) ?: return null
			val item = childByName(parentNodeInfo.id, parentNodeInfo.driveId, node.name)
			return if (item == null) {
				null
			} else {
				OnedriveIdCache.NodeInfo(getId(item), getDriveId(item), isFolder(item), item.cTag)
			}
		} ?: throw ParentFolderIsNullException(node.name)
	}

	fun currentAccount(): String {
		return client().me().drive().buildRequest().get().owner.user.displayName
	}

	fun logout() {
		val result = CompletableFuture<Void?>()
		OnedriveClientFactory.getAuthAdapter(context, cloud.accessToken()).logout(object : ICallback<Void?> {
			override fun success(aVoid: Void?) {
				result.complete(null)
			}

			override fun failure(e: ClientException) {
				result.completeExceptionally(e)
			}
		})
		try {
			result.get()
		} catch (e: InterruptedException) {
			throw FatalBackendException(e)
		} catch (e: ExecutionException) {
			throw FatalBackendException(e)
		}
	}

	companion object {

		private const val CHUNKED_UPLOAD_MAX_SIZE = 4L shl 20
		private const val CHUNKED_UPLOAD_CHUNK_SIZE = 327680 * 32
		private const val CHUNKED_UPLOAD_MAX_ATTEMPTS = 5
		private const val REPLACE_MODE = "replace"
		private const val NON_REPLACING_MODE = "rename"
	}

	init {
		if (cloud.accessToken() == null) {
			throw NoAuthenticationProvidedException(cloud)
		}
		this.cloud = cloud
		this.context = context
		this.nodeInfoCache = nodeInfoCache
		sharedPreferencesHandler = SharedPreferencesHandler(context)
	}
}
