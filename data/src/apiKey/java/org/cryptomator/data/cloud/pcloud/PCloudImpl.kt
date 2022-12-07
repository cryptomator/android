package org.cryptomator.data.cloud.pcloud

import android.content.Context
import com.pcloud.sdk.ApiClient
import com.pcloud.sdk.ApiError
import com.pcloud.sdk.DataSink
import com.pcloud.sdk.DownloadOptions
import com.pcloud.sdk.ProgressListener
import com.pcloud.sdk.RemoteFile
import com.pcloud.sdk.UploadOptions
import com.pcloud.sdk.internal.networking.APIHttpException
import com.tomclaw.cache.DiskLruCache
import org.cryptomator.data.cloud.pcloud.PCloudApiError.isCloudNodeAlreadyExistsException
import org.cryptomator.data.cloud.pcloud.PCloudApiError.isForbiddenException
import org.cryptomator.data.cloud.pcloud.PCloudApiError.isNetworkConnectionException
import org.cryptomator.data.cloud.pcloud.PCloudApiError.isNoSuchCloudFileException
import org.cryptomator.data.cloud.pcloud.PCloudApiError.isUnauthorizedException
import org.cryptomator.data.cloud.pcloud.PCloudApiError.isWrongCredentialsException
import org.cryptomator.data.util.CopyStream
import org.cryptomator.domain.PCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.ForbiddenException
import org.cryptomator.domain.exception.NetworkConnectionException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.exception.UnauthorizedException
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
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
import java.util.Date
import kotlin.math.pow
import okio.BufferedSink
import okio.BufferedSource
import okio.source
import timber.log.Timber

internal class PCloudImpl(context: Context, cloud: PCloud) {

	private val cloud: PCloud
	private val root: RootPCloudFolder
	private val context: Context
	private val sharedPreferencesHandler: SharedPreferencesHandler
	private var diskLruCache: DiskLruCache? = null

	private val apiClient: ApiClient by lazy {
		PCloudClientFactory.getInstance(cloud.accessToken(), cloud.url(), context)
	}

	fun root(): PCloudFolder {
		return root
	}

	@Throws(IOException::class, BackendException::class)
	fun resolve(path: String): PCloudFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder: PCloudFolder = root
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	@Throws(BackendException::class, IOException::class)
	fun file(parent: PCloudFolder, name: String, size: Long?): PCloudFile {
		return PCloudNodeFactory.file(parent, name, size, parent.path + "/" + name)
	}

	@Throws(IOException::class, BackendException::class)
	fun folder(parent: PCloudFolder, name: String): PCloudFolder {
		return PCloudNodeFactory.folder(parent, name, parent.path + "/" + name)
	}

	@Throws(IOException::class, BackendException::class)
	fun exists(node: PCloudNode): Boolean {
		return try {
			when (node) {
				is RootPCloudFolder -> {
					apiClient.loadFolder("/").execute()
				}
				is PCloudFolder -> {
					apiClient.loadFolder(node.path).execute()
				}
				else -> {
					apiClient.loadFile(node.path).execute()
				}
			}
			true
		} catch (ex: ApiError) {
			handleApiError(ex, PCloudApiError.ignoreExistsSet, node.name)
			false
		}
	}

	@Throws(IOException::class, BackendException::class)
	fun list(folder: PCloudFolder): List<PCloudNode> {
		val path = if (folder !is RootPCloudFolder) {
			folder.path
		} else {
			"/"
		}

		return try {
			apiClient
				.listFolder(path)
				.execute()
				.children()
				.map { node -> PCloudNodeFactory.from(folder, node) }
		} catch (ex: ApiError) {
			handleApiError(ex, folder.name)
			throw FatalBackendException(ex)
		}
	}

	@Throws(IOException::class, BackendException::class)
	fun create(folder: PCloudFolder): PCloudFolder {
		var folder = folder
		folder.parent?.let { parentFolder ->
			if (!exists(parentFolder)) {
				folder = PCloudFolder(create(parentFolder), folder.name, folder.path)
			}
		} ?: throw ParentFolderIsNullException(folder.name)

		folder.parent?.let { parentFolder ->
			return try {
				val createdFolder = apiClient //
					.createFolder(folder.path) //
					.execute()
				PCloudNodeFactory.folder(parentFolder, createdFolder)
			} catch (ex: ApiError) {
				handleApiError(ex, folder.name)
				throw FatalBackendException(ex)
			}
		} ?: throw ParentFolderIsNullException(folder.name)
	}

	@Throws(IOException::class, BackendException::class)
	fun move(source: PCloudNode, target: PCloudNode): PCloudNode {
		target.parent?.let { targetsParent ->
			if (exists(target)) {
				throw CloudNodeAlreadyExistsException(target.name)
			}
			return try {
				if (source is PCloudFolder) {
					PCloudNodeFactory.from(targetsParent, apiClient.moveFolder(source.path, target.path).execute())
				} else {
					PCloudNodeFactory.from(targetsParent, apiClient.moveFile(source.path, target.path).execute())
				}
			} catch (ex: ApiError) {
				when {
					isCloudNodeAlreadyExistsException(ex.errorCode()) -> {
						throw CloudNodeAlreadyExistsException(target.name)
					}
					isNoSuchCloudFileException(ex.errorCode()) -> {
						throw NoSuchCloudFileException(source.name)
					}
					else -> {
						handleApiError(ex, PCloudApiError.ignoreMoveSet, null)
					}
				}
				throw FatalBackendException(ex)
			}
		} ?: throw ParentFolderIsNullException(target.name)
	}

	@Throws(IOException::class, BackendException::class)
	fun write(file: PCloudFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): PCloudFile {
		if (!replace && exists(file)) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}
		progressAware.onProgress(Progress.started(UploadState.upload(file)))

		val uploadOptions = if (replace) {
			UploadOptions.OVERRIDE_FILE
		} else {
			UploadOptions.DEFAULT
		}

		val uploadedFile = uploadFile(file, data, progressAware, uploadOptions, size)
		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return PCloudNodeFactory.file(file.parent, uploadedFile)
	}

	@Throws(IOException::class, BackendException::class)
	private fun uploadFile(file: PCloudFile, data: DataSource, progressAware: ProgressAware<UploadState>, uploadOptions: UploadOptions, size: Long): RemoteFile {
		val listener = ProgressListener { done: Long, _: Long ->
			progressAware.onProgress( //
				Progress.progress(UploadState.upload(file)) //
					.between(0) //
					.and(size) //
					.withValue(done)
			)
		}
		val pCloudDataSource: com.pcloud.sdk.DataSource = object : com.pcloud.sdk.DataSource() {
			override fun contentLength(): Long {
				return data.size(context) ?: Long.MAX_VALUE
			}

			@Throws(IOException::class)
			override fun writeTo(sink: BufferedSink) {
				data.open(context)?.source()?.use { source -> sink.writeAll(source) }
			}
		}
		return try {
			apiClient //
				.createFile(file.parent.path, file.name, pCloudDataSource, Date(), listener, uploadOptions) //
				.execute()
		} catch (ex: ApiError) {
			handleApiError(ex, file.name)
			throw FatalBackendException(ex)
		}
	}

	@Throws(IOException::class, BackendException::class)
	fun read(file: PCloudFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		var cacheKey: String? = null
		var cacheFile: File? = null
		val remoteFile: RemoteFile
		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			try {
				remoteFile = apiClient.loadFile(file.path).execute().asFile()
				cacheKey = "${remoteFile.fileId()}${remoteFile.hash()}"
			} catch (ex: ApiError) {
				handleApiError(ex, file.name)
			}
			cacheFile = diskLruCache?.let { it[cacheKey] }
		}
		if (sharedPreferencesHandler.useLruCache() && cacheFile != null) {
			try {
				retrieveFromLruCache(cacheFile, data)
			} catch (e: IOException) {
				Timber.tag("PCloudImpl").w(e, "Error while retrieving content from Cache, get from web request")
				writeToData(file, data, encryptedTmpFile, cacheKey, progressAware)
			}
		} else {
			writeToData(file, data, encryptedTmpFile, cacheKey, progressAware)
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	@Throws(IOException::class, BackendException::class)
	private fun writeToData(
		file: PCloudFile,  //
		data: OutputStream,  //
		encryptedTmpFile: File?,  //
		cacheKey: String?,  //
		progressAware: ProgressAware<DownloadState>
	) {
		val listener = ProgressListener { done: Long, total: Long ->
			progressAware.onProgress( //
				Progress.progress(DownloadState.download(file)) //
					.between(0) //
					.and(total) //
					.withValue(done)
			)
		}

		val sink: DataSink = object : DataSink() {
			override fun readAll(source: BufferedSource) {
				CopyStream.copyStreamToStream(source.inputStream(), data)
			}
		}

		readFile(file.path, sink, listener)
		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile != null && cacheKey != null) {
			try {
				diskLruCache?.let {
					LruFileCacheUtil.storeToLruCache(it, cacheKey, encryptedTmpFile)
				} ?: Timber.tag("PCloudImpl").e("Failed to store item in LRU cache")
			} catch (e: IOException) {
				Timber.tag("PCloudImpl").e(e, "Failed to write downloaded file in LRU cache")
			}
		}
	}

	private fun readFile(filePath : String, sink: DataSink, listener : ProgressListener) {
		var attempts = 0
		while (++attempts <= MaxContentLinkDownloadAttempts) {
			val fileLink = apiClient.createFileLink(filePath, DownloadOptions.DEFAULT).execute()
			try {
				// Attempt to download the link's content, starting with the best link variant.
				for (url in fileLink.urls()) {
					try {
						fileLink.download(url, sink, listener)
					} catch (e : APIHttpException) {
						// HTTP 404's denote that the file may have been moved on another
						// storage service node.
						if (e.code == 404) {
							// Check if more link variants are available, either try opening
							// the next variant or give up by fall-through and throwing.
							if (url != fileLink.urls().last()) {
								continue
							}
						}

						throw e
					}
				}
			} catch (e: APIHttpException) {
				if (e.code == 410/* Gone */) {
					// The link to the file's content has expired or became otherwise invalid
					// due to a network switch, signalled with a `410 - Gone` HTTP error code.
					//
					// Content links have a very limited lifetime and apart form the time expiration
					// they are restricted to be used only from the IP that was used when making the
					// API call for generating them.
					//
					// The IP-switching limitation can be hit quite easily on mobile devices with multiple
					// sources of connectivity (mobile/wifi/...) where the system will follow
					// a strategy that aims to use the fastest and cheapest (non-metered) network
					// present at the moment.

					// Purge cached connections from OkHttp to potentially avoid any
					// new IP-switch issues where the opened connections of the previously-active network
					// have not yet been terminated by the systems network manager.
					//
					// For more insight and details on the network change behavior on Android, see:
					// https://developer.android.com/training/basics/network-ops/reading-network-state
					apiClient.connectionPool().evictAll()

					// Attempt to generate a new link (with a backoff delay) on the new network
					// or if the maximum attempt count has been reached give up by
					// falling-through and throwing.
					if (attempts < MaxContentLinkDownloadAttempts) {
						val nextSleepPeriodMs = ((attempts - 1f).pow(2f)
								* ContentLinkDownloadAttemptDelayStepMs).toLong()
						Thread.sleep(nextSleepPeriodMs)
						continue
					}
				}
				throw e
			}
		}
	}

	@Throws(IOException::class, BackendException::class)
	fun delete(node: PCloudNode) {
		try {
			if (node is PCloudFolder) {
				apiClient.deleteFolder(node.path, true).execute()
			} else {
				apiClient.deleteFile(node.path).execute()
			}
		} catch (ex: ApiError) {
			handleApiError(ex, node.name)
		}
	}

	@Throws(IOException::class, BackendException::class)
	fun currentAccount(): String {
		return try {
			apiClient //
				.userInfo //
				.execute() //
				.email()
		} catch (ex: ApiError) {
			handleApiError(ex)
			throw FatalBackendException(ex)
		}
	}

	private fun createLruCache(cacheSize: Int): Boolean {
		if (diskLruCache == null) {
			diskLruCache = try {
				DiskLruCache.create(LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.PCLOUD), cacheSize.toLong())
			} catch (e: IOException) {
				Timber.tag("PCloudImpl").e(e, "Failed to setup LRU cache")
				return false
			}
		}
		return true
	}

	@Throws(BackendException::class)
	private fun handleApiError(ex: ApiError, name: String) {
		handleApiError(ex, null, name)
	}

	@Throws(BackendException::class)
	private fun handleApiError(ex: ApiError, errorCodes: Set<Int>? = null, name: String? = null) {
		if (errorCodes == null || !errorCodes.contains(ex.errorCode())) {
			val errorCode = ex.errorCode()
			when {
				isCloudNodeAlreadyExistsException(errorCode) -> {
					throw CloudNodeAlreadyExistsException(name)
				}
				isForbiddenException(errorCode) -> {
					throw ForbiddenException()
				}
				isNetworkConnectionException(errorCode) -> {
					throw NetworkConnectionException(ex)
				}
				isNoSuchCloudFileException(errorCode) -> {
					throw NoSuchCloudFileException(name)
				}
				isWrongCredentialsException(errorCode) -> {
					throw WrongCredentialsException(cloud)
				}
				isUnauthorizedException(errorCode) -> {
					throw UnauthorizedException()
				}
				else -> {
					throw FatalBackendException(ex)
				}
			}
		}
	}

	fun logout() {
		PCloudClientFactory.logout()
	}

	init {
		if (cloud.accessToken() == null) {
			throw NoAuthenticationProvidedException(cloud)
		}
		this.context = context
		this.cloud = cloud
		this.root = RootPCloudFolder(cloud)
		sharedPreferencesHandler = SharedPreferencesHandler(context)
	}

	companion object {
		private const val MaxContentLinkDownloadAttempts = 5
		private const val ContentLinkDownloadAttemptDelayStepMs = 200L
	}
}
