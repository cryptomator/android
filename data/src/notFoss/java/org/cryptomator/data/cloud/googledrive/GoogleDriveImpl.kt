package org.cryptomator.data.cloud.googledrive

import android.content.Context
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.HttpResponseException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Revision
import com.tomclaw.cache.DiskLruCache
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.GoogleDriveCloud
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
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayList
import timber.log.Timber

internal class GoogleDriveImpl(context: Context, googleDriveCloud: GoogleDriveCloud, idCache: GoogleDriveIdCache) {

	private val idCache: GoogleDriveIdCache
	private val context: Context
	private val googleDriveCloud: GoogleDriveCloud
	private val sharedPreferencesHandler: SharedPreferencesHandler
	private val root: RootGoogleDriveFolder
	private var diskLruCache: DiskLruCache? = null

	private fun client(): Drive {
		return GoogleDriveClientFactory.getInstance(googleDriveCloud.accessToken(), context)
	}

	fun root(): GoogleDriveFolder {
		return root
	}

	@Throws(IOException::class)
	fun resolve(path: String): GoogleDriveFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder: GoogleDriveFolder = root
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	@Throws(IOException::class)
	private fun findFile(parentDriveId: String?, name: String): File? {
		val fileListQuery = client().files().list() //
			.setFields("files(id,mimeType,name,size)") //
			.setSupportsAllDrives(true)
		fileListQuery.q = if (parentDriveId != null && parentDriveId == "root") {
			"name contains '$name' and '$parentDriveId' in parents and trashed = false or sharedWithMe"
		} else {
			"name contains '$name' and '$parentDriveId' in parents and trashed = false"
		}
		return fileListQuery.execute().files.firstOrNull { it.name == name }
	}

	@Throws(IOException::class)
	fun file(parent: GoogleDriveFolder, name: String, size: Long?): GoogleDriveFile {
		if (parent.driveId == null) {
			return GoogleDriveCloudNodeFactory.file(parent, name, size)
		}
		val path = GoogleDriveCloudNodeFactory.getNodePath(parent, name)
		val nodeInfo = idCache[path]
		if (nodeInfo != null && !nodeInfo.isFolder) {
			requireNotNull(nodeInfo.id)
			return GoogleDriveCloudNodeFactory.file(parent, name, size, path, nodeInfo.id)
		}

		findFile(parent.driveId, name)?.let {
			if (!GoogleDriveCloudNodeFactory.isFolder(it)) {
				return idCache.cache(GoogleDriveCloudNodeFactory.file(parent, it))
			}
		}

		return GoogleDriveCloudNodeFactory.file(parent, name, size)
	}

	@Throws(IOException::class)
	fun folder(parent: GoogleDriveFolder, name: String): GoogleDriveFolder {
		if (parent.driveId == null) {
			return GoogleDriveCloudNodeFactory.folder(parent, name)
		}
		val path = GoogleDriveCloudNodeFactory.getNodePath(parent, name)
		val nodeInfo = idCache[path]
		if (nodeInfo != null && nodeInfo.isFolder) {
			requireNotNull(nodeInfo.id)
			return GoogleDriveCloudNodeFactory.folder(parent, name, path, nodeInfo.id)
		}
		val folder = findFile(parent.driveId, name)

		folder?.let {
			if (GoogleDriveCloudNodeFactory.isFolder(it)) {
				return idCache.cache(GoogleDriveCloudNodeFactory.folder(parent, it))
			}
		}

		return GoogleDriveCloudNodeFactory.folder(parent, name)
	}

	@Throws(IOException::class)
	fun exists(node: GoogleDriveNode): Boolean {
		return try {
			node.parent?.let { nodesParent ->
				findFile(nodesParent.driveId, node.name)?.let { idCache.add(GoogleDriveCloudNodeFactory.from(nodesParent, it)) } != null
			} ?: throw ParentFolderIsNullException(node.name)
		} catch (e: GoogleJsonResponseException) {
			if (e.statusCode == 404) {
				return false
			}
			throw e
		}
	}

	@Throws(IOException::class)
	fun list(folder: GoogleDriveFolder): List<GoogleDriveNode> {
		val result: MutableList<GoogleDriveNode> = ArrayList()
		var pageToken: String? = null
		do {
			val fileListQuery = client() //
				.files() //
				.list() //
				.setFields("nextPageToken,files(id,mimeType,modifiedTime,name,size)") //
				.setPageSize(1000) //
				.setSupportsAllDrives(true) //
				.setIncludeItemsFromAllDrives(true) //
				.setPageToken(pageToken)
			if (folder.driveId == "root") {
				fileListQuery.q = "'" + folder.driveId + "' in parents and trashed = false or sharedWithMe"
			} else {
				fileListQuery.q = "'" + folder.driveId + "' in parents and trashed = false"
			}
			val fileList = fileListQuery.execute()
			for (file in fileList.files) {
				result.add(idCache.cache(GoogleDriveCloudNodeFactory.from(folder, file)))
			}
			pageToken = fileList.nextPageToken
		} while (pageToken != null)
		return result
	}

	@Throws(IOException::class)
	fun create(folder: GoogleDriveFolder): GoogleDriveFolder {
		var folder = folder

		if (folder.parent?.driveId == null) {
			folder.parent?.let {
				folder = GoogleDriveFolder(create(it), folder.name, folder.path, folder.driveId)
			} ?: throw ParentFolderIsNullException(folder.name)
		}

		folder.parent?.let { parentFolder ->
			val metadata = File()
			metadata.name = folder.name
			metadata.mimeType = "application/vnd.google-apps.folder"
			metadata.parents = listOf(parentFolder.driveId)
			val createdFolder = client() //
				.files() //
				.create(metadata) //
				.setFields("id,name") //
				.setSupportsAllDrives(true) //
				.execute()
			return idCache.cache(GoogleDriveCloudNodeFactory.folder(parentFolder, createdFolder))
		} ?: throw ParentFolderIsNullException(folder.name)
	}

	@Throws(IOException::class, CloudNodeAlreadyExistsException::class)
	fun move(source: GoogleDriveNode, target: GoogleDriveNode): GoogleDriveNode {
		if (exists(target)) {
			throw CloudNodeAlreadyExistsException(target.name)
		}

		source.parent?.let { sourcesParent ->
			target.parent?.let { targetsParent ->
				val metadata = File()
				metadata.name = target.name
				val movedFile = client() //
					.files() //
					.update(source.driveId, metadata) //
					.setFields("id,mimeType,modifiedTime,name,size") //
					.setAddParents(targetsParent.driveId) //
					.setRemoveParents(sourcesParent.driveId)  //
					.setSupportsAllDrives(true) //
					.execute()
				idCache.remove(source)
				return idCache.cache(GoogleDriveCloudNodeFactory.from(targetsParent, movedFile))
			} ?: throw ParentFolderIsNullException(target.name)
		} ?: throw ParentFolderIsNullException(source.name)
	}

	@Throws(IOException::class, BackendException::class)
	fun write(file: GoogleDriveFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): GoogleDriveFile {
		if (!replace && exists(file)) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}
		if (file.parent.driveId == null) {
			throw NoSuchCloudFileException(String.format("The parent folder of %s doesn't have a driveId. The file would remain in root folder", file.path))
		}
		val metadata = File()
		metadata.name = file.name
		progressAware.onProgress(Progress.started(UploadState.upload(file)))
		val uploadedFile = if (file.driveId != null && replace) {
			updateFile(file, data, progressAware, size, metadata)
		} else {
			createNewFile(file, data, progressAware, size, metadata)
		} ?: throw FatalBackendException("InputStream shouldn't be null")
		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return idCache.cache(GoogleDriveCloudNodeFactory.file(file.parent, uploadedFile))
	}

	private fun updateFile(file: GoogleDriveFile, data: DataSource, progressAware: ProgressAware<UploadState>, size: Long, metadata: File): File? {
		return data.open(context)?.use { inputStream ->
			object : TransferredBytesAwareGoogleContentInputStream(null, inputStream, size) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress( //
						Progress.progress(UploadState.upload(file)) //
							.between(0) //
							.and(size) //
							.withValue(transferred)
					)
				}
			}.use {
				client() //
					.files() //
					.update(file.driveId, metadata, it) //
					.setFields("id,modifiedTime,name,size") //
					.setSupportsAllDrives(true) //
					.execute()
			}
		}
	}

	private fun createNewFile(file: GoogleDriveFile, data: DataSource, progressAware: ProgressAware<UploadState>, size: Long, metadata: File): File? {
		return data.open(context)?.use { inputStream ->
			metadata.parents = listOf(file.parent.driveId)
			object : TransferredBytesAwareGoogleContentInputStream(null, inputStream, size) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress( //
						Progress.progress(UploadState.upload(file)) //
							.between(0) //
							.and(size) //
							.withValue(transferred)
					)
				}
			}.use {
				client() //
					.files() //
					.create(metadata, it) //
					.setFields("id,modifiedTime,name,size") //
					.setSupportsAllDrives(true) //
					.execute()
			}
		}
	}

	@Throws(IOException::class)
	fun read(file: GoogleDriveFile, encryptedTmpFile: java.io.File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		var cacheKey: String? = null
		var cacheFile: java.io.File? = null
		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			cacheKey = file.driveId + getRevisionIdFor(file)
			cacheFile = diskLruCache?.let { it[cacheKey] }
		}
		if (sharedPreferencesHandler.useLruCache() && cacheFile != null) {
			try {
				retrieveFromLruCache(cacheFile, data)
			} catch (e: IOException) {
				Timber.tag("GoogleDriveImpl").w(e, "Error while retrieving content from Cache, get from web request")
				writeToDate(file, data, encryptedTmpFile, cacheKey, progressAware)
			}
		} else {
			writeToDate(file, data, encryptedTmpFile, cacheKey, progressAware)
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	private fun getRevisionIdFor(file: GoogleDriveFile): String? {
		val revisions: MutableList<Revision> = ArrayList()
		var pageToken: String? = null
		do {
			val revisionList = client() //
				.revisions() //
				.list(file.driveId) //
				.setPageToken(pageToken) //
				.execute()
			revisions.addAll(revisionList.revisions)
			pageToken = revisionList.nextPageToken
		} while (pageToken != null)
		revisions.sortWith { revision1: Revision, revision2: Revision ->
			val modified1 = revision1.modifiedTime.value
			val modified2 = revision2.modifiedTime.value
			modified1.compareTo(modified2).compareTo(0)
		}
		val revisionIndex = if (revisions.size > 0) revisions.size - 1 else 0
		return revisions[revisionIndex].id
	}

	@Throws(IOException::class)
	private fun writeToDate(
		file: GoogleDriveFile,  //
		data: OutputStream,  //
		encryptedTmpFile: java.io.File?,  //
		cacheKey: String?,  //
		progressAware: ProgressAware<DownloadState>
	) {
		try {
			object : TransferredBytesAwareOutputStream(data) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress( //
						Progress.progress(DownloadState.download(file)) //
							.between(0) //
							.and(file.size ?: Long.MAX_VALUE) //
							.withValue(transferred)
					)
				}
			}.use {
				client() //
					.files()[file.driveId] //
					.setAlt("media") //
					.setSupportsAllDrives(true) //
					.executeMediaAndDownloadTo(it)
			}
		} catch (e: HttpResponseException) {
			ignoreEmptyFileErrorAndRethrowOthers(e, file)
		}
		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile != null && cacheKey != null) {
			try {
				diskLruCache?.let {
					LruFileCacheUtil.storeToLruCache(it, cacheKey, encryptedTmpFile)
				} ?: Timber.tag("GoogleDriveImpl").e("Failed to store item in LRU cache")
			} catch (e: IOException) {
				Timber.tag("GoogleDriveImpl").e(e, "Failed to write downloaded file in LRU cache")
			}
		}
	}

	private fun createLruCache(cacheSize: Int): Boolean {
		if (diskLruCache == null) {
			diskLruCache = try {
				DiskLruCache.create(LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.GOOGLE_DRIVE), cacheSize.toLong())
			} catch (e: IOException) {
				Timber.tag("GoogleDriveImpl").e(e, "Failed to setup LRU cache")
				return false
			}
		}
		return true
	}

	/*
	 * Workaround a bug in gdrive which does not allow to download empty files.
	 *
	 * In this case an HttpResponseException with status code 416 is thrown. The filesize is checked.
	 * If zero, the exception is ignored - nothing has been read, so the OutputStream is in the correct
	 * state.
	 */
	@Throws(IOException::class)
	private fun ignoreEmptyFileErrorAndRethrowOthers(e: HttpResponseException, file: GoogleDriveFile) {
		if (e.statusCode == STATUS_REQUEST_RANGE_NOT_SATISFIABLE) {
			val foundFile = findFile( //
				file.parent.driveId,  //
				file.name
			)
			if (sizeOfFile(foundFile) == 0L) {
				return
			}
		}
		throw e
	}

	private fun sizeOfFile(foundFile: File?): Long {
		return if (foundFile == null || GoogleDriveCloudNodeFactory.isFolder(foundFile)) {
			-1
		} else foundFile.getSize()
	}

	@Throws(IOException::class)
	fun delete(node: GoogleDriveNode) {
		client().files().delete(node.driveId).setSupportsAllDrives(true).execute()
		idCache.remove(node)
	}

	@Throws(IOException::class)
	fun currentAccount(): String {
		val about = client().about().get().execute()
		return about.user.displayName
	}

	companion object {

		private const val STATUS_REQUEST_RANGE_NOT_SATISFIABLE = 416
	}

	init {
		if (googleDriveCloud.accessToken() == null) {
			throw NoAuthenticationProvidedException(googleDriveCloud)
		}
		this.context = context
		this.googleDriveCloud = googleDriveCloud
		this.idCache = idCache
		this.root = RootGoogleDriveFolder(googleDriveCloud)
		sharedPreferencesHandler = SharedPreferencesHandler(context)
	}
}
