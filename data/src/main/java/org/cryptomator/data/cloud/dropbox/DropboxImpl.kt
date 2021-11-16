package org.cryptomator.data.cloud.dropbox

import android.content.Context
import com.dropbox.core.DbxException
import com.dropbox.core.NetworkIOException
import com.dropbox.core.RetryException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CommitInfo
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.GetMetadataErrorException
import com.dropbox.core.v2.files.ListFolderResult
import com.dropbox.core.v2.files.UploadSessionCursor
import com.dropbox.core.v2.files.UploadSessionFinishErrorException
import com.dropbox.core.v2.files.UploadSessionLookupErrorException
import com.dropbox.core.v2.files.WriteMode
import com.tomclaw.cache.DiskLruCache
import org.cryptomator.data.cloud.dropbox.DropboxCloudNodeFactory.file
import org.cryptomator.data.cloud.dropbox.DropboxCloudNodeFactory.folder
import org.cryptomator.data.cloud.dropbox.DropboxCloudNodeFactory.from
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.DropboxCloud
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.exception.authentication.AuthenticationException
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
import timber.log.Timber

internal class DropboxImpl(cloud: DropboxCloud, context: Context) {

	private val cloud: DropboxCloud
	private val root: RootDropboxFolder
	private val context: Context
	private val sharedPreferencesHandler: SharedPreferencesHandler
	private var diskLruCache: DiskLruCache? = null

	@Throws(AuthenticationException::class)
	private fun client(): DbxClientV2 {
		return DropboxClientFactory.getInstance(cloud.accessToken(), context)
	}

	fun root(): DropboxFolder {
		return root
	}

	fun resolve(path: String): DropboxFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder: DropboxFolder = root
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	fun file(folder: DropboxFolder, name: String, size: Long?): DropboxFile {
		return file(folder, name, size, folder.path + '/' + name)
	}

	fun folder(folder: DropboxFolder, name: String): DropboxFolder {
		return folder(folder, name, folder.path + '/' + name)
	}

	@Throws(AuthenticationException::class, DbxException::class)
	fun exists(node: CloudNode): Boolean {
		return try {
			val metadata = client() //
				.files() //
				.getMetadata(node.path)
			if (node is CloudFolder) {
				metadata is FolderMetadata
			} else {
				metadata is FileMetadata
			}
		} catch (e: GetMetadataErrorException) {
			if (e.errorValue.isPath) {
				return false
			}
			throw e
		}
	}

	@Throws(AuthenticationException::class, DbxException::class)
	fun list(folder: DropboxFolder): List<DropboxNode> {
		val result = ArrayList<DropboxNode>()
		var listFolderResult: ListFolderResult? = null
		do {
			listFolderResult = if (listFolderResult == null) {
				client().files().listFolder(folder.path)
			} else {
				client().files().listFolderContinue(listFolderResult.cursor)
			}
			listFolderResult.entries.forEach {
				result.add(from(folder, it))
			}
		} while (listFolderResult?.hasMore == true)
		return result
	}

	@Throws(AuthenticationException::class, DbxException::class)
	fun create(folder: DropboxFolder): DropboxFolder {
		folder.parent?.let {
			val createFolderResult = client().files().createFolderV2(folder.path)
			return from(it, createFolderResult.metadata)
		} ?: throw ParentFolderIsNullException(folder.name)
	}

	@Throws(AuthenticationException::class, DbxException::class)
	fun move(source: DropboxNode, target: DropboxNode): DropboxNode {
		target.parent?.let { targetsParent ->
			val relocationResult = client().files().moveV2(source.path, target.path)
			return from(targetsParent, relocationResult.metadata)
		} ?: throw ParentFolderIsNullException(target.name)
	}

	@Throws(AuthenticationException::class, DbxException::class, IOException::class, CloudNodeAlreadyExistsException::class)
	fun write(file: DropboxFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): DropboxFile {
		if (!replace && exists(file)) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}
		progressAware.onProgress(Progress.started(UploadState.upload(file)))
		var writeMode = WriteMode.ADD
		if (replace) {
			writeMode = WriteMode.OVERWRITE
		}
		// "Upload the file with simple upload API if it is small enough, otherwise use chunked
		// upload API for better performance. Arbitrarily chose 2 times our chunk size as the
		// deciding factor. This should really depend on your network."
		// Source: https://github.com/dropbox/dropbox-sdk-java/blob/master/examples/upload-file/src/main/java/com/dropbox/core/examples/upload_file/Main.java
		if (size <= 2 * CHUNKED_UPLOAD_CHUNK_SIZE) {
			uploadFile(file, data, progressAware, writeMode, size)
		} else {
			chunkedUploadFile(file, data, progressAware, writeMode, size)
		}
		val metadata = client().files().getMetadata(file.path)
		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return from(file.parent, metadata) as DropboxFile
	}

	@Throws(AuthenticationException::class, DbxException::class, IOException::class)
	private fun uploadFile(file: DropboxFile, data: DataSource, progressAware: ProgressAware<UploadState>, writeMode: WriteMode, size: Long) {
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
			}.use {
				client() //
					.files() //
					.uploadBuilder(file.path) //
					.withMode(writeMode) //
					.uploadAndFinish(it)
			}
		} ?: Timber.tag("").e("InputStream shouldn't be null")
	}

	@Throws(AuthenticationException::class, DbxException::class, IOException::class)
	private fun chunkedUploadFile(file: DropboxFile, data: DataSource, progressAware: ProgressAware<UploadState>, writeMode: WriteMode, size: Long) {
		// Assert our file is at least the chunk upload size. We make this assumption in the code
		// below to simplify the logic.
		if (size < CHUNKED_UPLOAD_CHUNK_SIZE) {
			throw FatalBackendException("File too small, use uploadFile() instead.")
		}
		var uploaded = 0L
		var thrown: DbxException? = null
		data.open(context)?.use {

			// Chunked uploads have 3 phases, each of which can accept uploaded bytes:
			//
			// (1) Start: initiate the upload and get an upload session ID
			// (2) Append: upload chunks of the file to append to our session
			// (3) Finish: commit the upload and close the session
			//
			// We track how many bytes we uploaded to determine which phase we should be in.
			var sessionId: String? = null
			for (i in 0 until CHUNKED_UPLOAD_MAX_ATTEMPTS) {
				if (i > 0) {
					Timber.v("Retrying chunked upload (" + (i + 1) + " / " + CHUNKED_UPLOAD_MAX_ATTEMPTS + " attempts)")
				}
				try {
					// if this is a retry, make sure seek to the correct offset
					it.skip(uploaded)

					// (1) Start
					if (sessionId == null) {
						sessionId = client() //
							.files() //
							.uploadSessionStart() //
							.uploadAndFinish(object : TransferredBytesAwareInputStream(it) {
								override fun bytesTransferred(transferred: Long) {
									progressAware.onProgress( //
										Progress.progress(UploadState.upload(file)) //
											.between(0) //
											.and(size) //
											.withValue(transferred)
									)
								}
							}, CHUNKED_UPLOAD_CHUNK_SIZE).sessionId
						uploaded += CHUNKED_UPLOAD_CHUNK_SIZE
						progressAware.onProgress( //
							Progress.progress(UploadState.upload(file)) //
								.between(0) //
								.and(size) //
								.withValue(uploaded)
						)
					}
					var cursor = UploadSessionCursor(sessionId, uploaded)

					// (2) Append
					while (size - uploaded > CHUNKED_UPLOAD_CHUNK_SIZE) {
						val fullyUploaded = uploaded
						client() //
							.files() //
							.uploadSessionAppendV2(cursor) //
							.uploadAndFinish(object : TransferredBytesAwareInputStream(it) {
								override fun bytesTransferred(transferred: Long) {
									progressAware.onProgress( //
										Progress.progress(UploadState.upload(file)) //
											.between(0) //
											.and(size) //
											.withValue(fullyUploaded + transferred)
									)
								}
							}, CHUNKED_UPLOAD_CHUNK_SIZE)
						uploaded += CHUNKED_UPLOAD_CHUNK_SIZE
						progressAware.onProgress( //
							Progress.progress(UploadState.upload(file)) //
								.between(0) //
								.and(size) //
								.withValue(uploaded)
						)
						cursor = UploadSessionCursor(sessionId, uploaded)
					}

					// (3) Finish
					val remaining = size - uploaded
					val commitInfo = CommitInfo //
						.newBuilder(file.path) //
						.withMode(writeMode) //
						.build()
					client() //
						.files() //
						.uploadSessionFinish(cursor, commitInfo) //
						.uploadAndFinish(it, remaining)
					return
				} catch (ex: RetryException) {
					thrown = ex
					// RetryExceptions are never automatically retried by the client for uploads. Must
					// catch this exception even if DbxRequestConfig.getMaxRetries() > 0.
					sleepQuietly(ex.backoffMillis)
				} catch (ex: NetworkIOException) {
					thrown = ex
					// Network issue with Dropbox (maybe a timeout?), try again.
				} catch (ex: UploadSessionLookupErrorException) {
					if (ex.errorValue.isIncorrectOffset) {
						thrown = ex
						// Server offset into the stream doesn't match our offset (uploaded). Seek to
						// the expected offset according to the server and try again.
						uploaded = ex.errorValue.incorrectOffsetValue.correctOffset
					} else {
						throw FatalBackendException(ex)
					}
				} catch (ex: UploadSessionFinishErrorException) {
					if (ex.errorValue.isLookupFailed && ex.errorValue.lookupFailedValue.isIncorrectOffset) {
						thrown = ex
						// Server offset into the stream doesn't match our offset (uploaded). Seek to
						// the expected offset according to the server and try again.
						uploaded = ex.errorValue.lookupFailedValue.incorrectOffsetValue.correctOffset
					} else {
						throw FatalBackendException(ex)
					}
				}
			}
		} ?: throw FatalBackendException("InputStream is null")
		throw FatalBackendException("Maxed out upload attempts to Dropbox.", thrown)
	}

	@Throws(DbxException::class, IOException::class)
	fun read(file: CloudFile, encryptedTmpFile: File?, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		var cacheKey: String? = null
		var cacheFile: File? = null
		if (sharedPreferencesHandler.useLruCache() && createLruCache(sharedPreferencesHandler.lruCacheSize())) {
			val fileMetadata = client() //
				.files() //
				.getMetadata(file.path) as FileMetadata
			cacheKey = fileMetadata.id + fileMetadata.rev
			cacheFile = diskLruCache?.let { it[cacheKey] }
		}
		if (sharedPreferencesHandler.useLruCache() && cacheFile != null) {
			try {
				retrieveFromLruCache(cacheFile, data)
			} catch (e: IOException) {
				Timber.tag("DropboxImpl").w(e, "Error while retrieving content from Cache, get from web request")
				writeToData(file, data, encryptedTmpFile, cacheKey, progressAware)
			}
		} else {
			writeToData(file, data, encryptedTmpFile, cacheKey, progressAware)
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	@Throws(DbxException::class, IOException::class)
	private fun writeToData(file: CloudFile, data: OutputStream, encryptedTmpFile: File?, cacheKey: String?, progressAware: ProgressAware<DownloadState>) {
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
				.files() //
				.download(file.path) //
				.download(it)
		}
		if (sharedPreferencesHandler.useLruCache() && encryptedTmpFile != null && cacheKey != null) {
			try {
				diskLruCache?.let {
					LruFileCacheUtil.storeToLruCache(it, cacheKey, encryptedTmpFile)
				} ?: Timber.tag("DropboxImpl").e("Failed to store item in LRU cache")
			} catch (e: IOException) {
				Timber.tag("DropboxImpl").e(e, "Failed to write downloaded file in LRU cache")
			}
		}
	}

	private fun createLruCache(cacheSize: Int): Boolean {
		if (diskLruCache == null) {
			diskLruCache = try {
				DiskLruCache.create(LruFileCacheUtil(context).resolve(LruFileCacheUtil.Cache.DROPBOX), cacheSize.toLong())
			} catch (e: IOException) {
				Timber.tag("DropboxImpl").e(e, "Failed to setup LRU cache")
				return false
			}
		}
		return true
	}

	@Throws(AuthenticationException::class, DbxException::class)
	fun delete(node: CloudNode) {
		client().files().deleteV2(node.path)
	}

	@Throws(AuthenticationException::class, DbxException::class)
	fun currentAccount(): String {
		val currentAccount = client().users().currentAccount
		return currentAccount.name.displayName
	}

	companion object {

		private const val CHUNKED_UPLOAD_CHUNK_SIZE = 8L shl 20
		private const val CHUNKED_UPLOAD_MAX_ATTEMPTS = 5
		private fun sleepQuietly(millis: Long) {
			try {
				Thread.sleep(millis)
			} catch (ex: InterruptedException) {
				throw FatalBackendException("Error uploading to Dropbox: interrupted during backoff.")
			}
		}
	}

	init {
		if (cloud.accessToken() == null) {
			throw NoAuthenticationProvidedException(cloud)
		}
		this.cloud = cloud
		this.root = RootDropboxFolder(cloud)
		this.context = context
		sharedPreferencesHandler = SharedPreferencesHandler(context)
	}
}
