package org.cryptomator.data.cloud.crypto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import com.tomclaw.cache.DiskLruCache
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.common.DecryptingReadableByteChannel
import org.cryptomator.cryptolib.common.EncryptingWritableByteChannel
import org.cryptomator.data.cloud.crypto.DirIdCache.DirIdInfo
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.EmptyDirFileException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoDirFileException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.DownloadFileReplacingProgressAware
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.UploadFileReplacingProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.FileBasedDataSource.Companion.from
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.ThumbnailsOption
import org.cryptomator.util.file.LruFileCacheUtil
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypeMap
import org.cryptomator.util.file.MimeTypes
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.LinkedList
import java.util.Queue
import java.util.UUID
import java.util.function.Supplier
import timber.log.Timber


abstract class CryptoImplDecorator(
	val context: Context,
	private val cryptor: Supplier<Cryptor>,
	val cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
	private val storageLocation: CloudFolder,
	val dirIdCache: DirIdCache,
	val shorteningThreshold: Int
) {

	@Volatile
	private var root: RootCryptoFolder? = null

	private val sharedPreferencesHandler = SharedPreferencesHandler(context)

	private var diskLruCache: MutableMap<LruFileCacheUtil.Cache, DiskLruCache?> = mutableMapOf()

	private val mimeTypes = MimeTypes(MimeTypeMap())

	protected fun getLruCacheFor(type : CloudType): DiskLruCache? {
		return getOrCreateLruCache(getCacheTypeFromCloudType(type), sharedPreferencesHandler.lruCacheSize())
	}
	private fun getOrCreateLruCache(key : LruFileCacheUtil.Cache, cacheSize: Int): DiskLruCache? {
		return diskLruCache.computeIfAbsent(key) {
			val where = LruFileCacheUtil(context).resolve(it)
			try {
				DiskLruCache.create(where, cacheSize.toLong())
			} catch (e: IOException) {
				Timber.tag("CryptoImplDecorator").e(e, "Failed to setup LRU cache for $where.name")
				null
			}
		}
	}

	private fun getCacheTypeFromCloudType(type : CloudType) : LruFileCacheUtil.Cache {
		return when (type) {
			CloudType.DROPBOX -> LruFileCacheUtil.Cache.DROPBOX
			CloudType.GOOGLE_DRIVE -> LruFileCacheUtil.Cache.GOOGLE_DRIVE
			CloudType.ONEDRIVE -> LruFileCacheUtil.Cache.ONEDRIVE
			CloudType.PCLOUD -> LruFileCacheUtil.Cache.PCLOUD
			CloudType.WEBDAV -> LruFileCacheUtil.Cache.WEBDAV
			CloudType.S3 -> LruFileCacheUtil.Cache.S3
			CloudType.LOCAL -> LruFileCacheUtil.Cache.LOCAL
			else -> throw IllegalStateException()
		}
	}

	@Throws(BackendException::class)
	abstract fun folder(cryptoParent: CryptoFolder, cleartextName: String): CryptoFolder

	abstract fun decryptName(dirId: String, encryptedName: String): String?

	@Throws(BackendException::class)
	abstract fun encryptName(cryptoParent: CryptoFolder, name: String): String

	abstract fun extractEncryptedName(ciphertextName: String): String?

	@Throws(BackendException::class)
	abstract fun list(cryptoFolder: CryptoFolder): List<CryptoNode>

	@Throws(BackendException::class)
	abstract fun encryptFolderName(cryptoFolder: CryptoFolder, name: String): String

	@Throws(BackendException::class)
	abstract fun symlink(cryptoParent: CryptoFolder, cleartextName: String, target: String): CryptoSymlink

	@Throws(BackendException::class)
	abstract fun create(folder: CryptoFolder): CryptoFolder

	@Throws(BackendException::class)
	abstract fun move(source: CryptoFolder, target: CryptoFolder): CryptoFolder

	@Throws(BackendException::class)
	abstract fun move(source: CryptoFile, target: CryptoFile): CryptoFile

	@Throws(BackendException::class)
	abstract fun delete(node: CloudNode)

	@Throws(BackendException::class)
	abstract fun write(cryptoFile: CryptoFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, length: Long): CryptoFile

	@Throws(BackendException::class, EmptyDirFileException::class)
	abstract fun loadDirId(folder: CryptoFolder): String?

	@Throws(BackendException::class)
	abstract fun getOrCreateDirIdInfo(folder: CryptoFolder): DirIdInfo

	@Throws(BackendException::class)
	abstract fun getDirIdInfo(folder: CryptoFolder): DirIdInfo?

	private fun dirHash(directoryId: String): String {
		return cryptor().fileNameCryptor().hashDirectoryId(directoryId)
	}

	@Throws(BackendException::class)
	private fun dataFolder(): CloudFolder {
		return cloudContentRepository.folder(storageLocation, CryptoConstants.DATA_DIR_NAME)
	}

	fun path(base: CloudFolder, name: String): String {
		return base.path + "/" + name
	}

	val internalCache: File
		get() = context.cacheDir

	@Throws(BackendException::class)
	fun deepCollectSubfolders(source: CryptoFolder): List<CryptoFolder> {

		val queue: Queue<CryptoFolder> = LinkedList()
		queue.add(source)
		val result: MutableList<CryptoFolder> = LinkedList()

		while (!queue.isEmpty()) {
			val folder = queue.remove()
			val subfolders = shallowCollectSubfolders(folder)
			queue.addAll(subfolders)
			result.addAll(subfolders)
		}

		result.reverse()
		return result
	}

	@Throws(BackendException::class)
	private fun shallowCollectSubfolders(source: CryptoFolder): List<CryptoFolder> {
		return try {
			list(source).filterIsInstance<CryptoFolder>()
		} catch (e: NoDirFileException) {
			// Ignoring because nothing can be done if the dir-file doesn't exists in the cloud
			emptyList()
		}
	}

	@Throws(BackendException::class)
	@Synchronized
	fun root(cryptoCloud: CryptoCloud): RootCryptoFolder = root ?: RootCryptoFolder(cryptoCloud).also { root = it }

	@Throws(BackendException::class)
	fun resolve(cloud: CryptoCloud, path: String): CryptoFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder: CryptoFolder = root(cloud)
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	@Throws(BackendException::class)
	fun file(cryptoParent: CryptoFolder, cleartextName: String): CryptoFile {
		return file(cryptoParent, cleartextName, null)
	}

	@Throws(BackendException::class)
	fun file(cryptoParent: CryptoFolder, cleartextName: String, cleartextSize: Long?): CryptoFile {
		val ciphertextName = encryptFileName(cryptoParent, cleartextName)
		return file(cryptoParent, cleartextName, ciphertextName, cleartextSize)
	}

	@Throws(BackendException::class)
	private fun file(cryptoParent: CryptoFolder, cleartextName: String, ciphertextName: String, cleartextSize: Long?): CryptoFile {
		val ciphertextSize = cleartextSize?.let { cryptor().fileContentCryptor().ciphertextSize(it) + cryptor().fileHeaderCryptor().headerSize() }
		val cloudFile = cloudContentRepository.file(getOrCreateCachingAwareDirIdInfo(cryptoParent).cloudFolder, ciphertextName, ciphertextSize)
		return file(cryptoParent, cleartextName, cloudFile, cleartextSize)
	}

	@Throws(BackendException::class)
	fun file(cryptoFile: CryptoFile, cloudFile: CloudFile, cleartextSize: Long?): CryptoFile {
		return file(cryptoFile.parent, cryptoFile.name, cloudFile, cleartextSize)
	}

	@Throws(BackendException::class)
	fun file(cryptoParent: CryptoFolder, cleartextName: String, cloudFile: CloudFile, cleartextSize: Long?): CryptoFile {
		return CryptoFile(cryptoParent, cleartextName, path(cryptoParent, cleartextName), cleartextSize, cloudFile)
	}

	@Throws(BackendException::class)
	private fun encryptFileName(cryptoParent: CryptoFolder, name: String): String {
		return encryptName(cryptoParent, name)
	}

	@Throws(BackendException::class)
	fun folder(cryptoParent: CryptoFolder, cleartextName: String, dirFile: CloudFile): CryptoFolder {
		return CryptoFolder(cryptoParent, cleartextName, path(cryptoParent, cleartextName), dirFile)
	}

	@Throws(BackendException::class)
	fun folder(cryptoFolder: CryptoFolder, dirFile: CloudFile): CryptoFolder {
		return CryptoFolder(cryptoFolder.parent, cryptoFolder.name, cryptoFolder.path, dirFile)
	}

	@Throws(BackendException::class)
	fun exists(node: CloudNode): Boolean {
		return when (node) {
			is CryptoFolder -> {
				exists(node)
			}
			is CryptoFile -> {
				exists(node)
			}
			is CryptoSymlink -> {
				exists(node)
			}
			else -> {
				throw IllegalArgumentException("Unexpected CloudNode type: " + node.javaClass)
			}
		}
	}

	@Throws(BackendException::class)
	private fun exists(folder: CryptoFolder): Boolean {
		requireNotNull(folder.dirFile)
		return cloudContentRepository.exists(folder.dirFile) && getCachingAwareDirIdInfo(folder)?.let {
			cloudContentRepository.exists(it.cloudFolder)
		} ?: false
	}

	@Throws(BackendException::class)
	private fun exists(file: CryptoFile): Boolean {
		return cloudContentRepository.exists(file.cloudFile)
	}

	@Throws(BackendException::class)
	private fun exists(symlink: CryptoSymlink): Boolean {
		return cloudContentRepository.exists(symlink.cloudFile)
	}

	@Throws(BackendException::class)
	fun assertCryptoFolderAlreadyExists(cryptoFolder: CryptoFolder) {
		requireNotNull(cryptoFolder.dirFile)
		requireNotNull(cryptoFolder.parent)
		cryptoFolder.parent?.let { cryptosParent ->
			if (cloudContentRepository.exists(cryptoFolder.dirFile)
				|| cloudContentRepository.exists(file(cryptosParent, cryptoFolder.name))
			) {
				throw CloudNodeAlreadyExistsException(cryptoFolder.name)
			}
		} ?: throw ParentFolderIsNullException(cryptoFolder.name)
	}

	@Throws(BackendException::class)
	fun assertCryptoFileAlreadyExists(cryptoFile: CryptoFile) {
		val dirFile = folder(cryptoFile.parent, cryptoFile.name).dirFile
		requireNotNull(dirFile)
		if (cloudContentRepository.exists(cryptoFile.cloudFile) //
			|| cloudContentRepository.exists(dirFile)
		) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}
	}

	@Throws(BackendException::class, IOException::class)
	private fun writeFromTmpFile(originalDataSource: DataSource, cryptoFile: CryptoFile, encryptedFile: File, progressAware: ProgressAware<UploadState>, replace: Boolean): CryptoFile {
		val targetFile = targetFile(cryptoFile, replace)
		return file(
			targetFile,  //
			cloudContentRepository.write( //
				targetFile.cloudFile,  //
				originalDataSource.decorate(from(encryptedFile)), //
				UploadFileReplacingProgressAware(cryptoFile, progressAware),  //
				replace,  //
				encryptedFile.length()
			),  //
			cryptoFile.size
		)
	}

	@Throws(BackendException::class)
	private fun targetFile(cryptoFile: CryptoFile, replace: Boolean): CryptoFile {
		return if (replace || !cloudContentRepository.exists(cryptoFile)) {
			cryptoFile
		} else firstNonExistingAutoRenamedFile(cryptoFile)
	}

	@Throws(BackendException::class)
	private fun firstNonExistingAutoRenamedFile(original: CryptoFile): CryptoFile {
		val name = original.name
		val nameWithoutExtension = nameWithoutExtension(name)
		val extension = extension(name)
		var counter = 1
		var result: CryptoFile
		do {
			val newFileName = "$nameWithoutExtension ($counter)$extension"
			result = file(original.parent, newFileName, original.size)
			counter++
		} while (cloudContentRepository.exists(result))
		return result
	}

	fun nameWithoutExtension(name: String): String {
		val lastDot = name.lastIndexOf(".")
		return if (lastDot == -1) {
			name
		} else name.substring(0, lastDot)
	}

	fun extension(name: String): String {
		val lastDot = name.lastIndexOf(".")
		return if (lastDot == -1) {
			""
		} else name.substring(lastDot + 1)
	}

	@Throws(BackendException::class)
	fun read(cryptoFile: CryptoFile, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		val ciphertextFile = cryptoFile.cloudFile

		val diskCache = cryptoFile.cloudFile.cloud?.type()?.let { getLruCacheFor(it) }
		val cacheKey = generateCacheKey(ciphertextFile)
		val genThumbnail = isGenerateThumbnailsEnabled(diskCache, cryptoFile.name)
		val decryptedTempFile : File
		try {
			val encryptedTmpFile = readToTmpFile(cryptoFile, ciphertextFile, progressAware)
			decryptedTempFile = File.createTempFile(encryptedTmpFile.nameWithoutExtension, ".tmp", internalCache)

			val decryptedTempFileOutputStream = decryptedTempFile.outputStream()
			progressAware.onProgress(Progress.started(DownloadState.decryption(cryptoFile)))
			try {
				Channels.newChannel(FileInputStream(encryptedTmpFile)).use { readableByteChannel ->
					DecryptingReadableByteChannel(readableByteChannel, cryptor(), true).use { decryptingReadableByteChannel ->
						val buff = ByteBuffer.allocate(cryptor().fileContentCryptor().ciphertextChunkSize())
						val cleartextSize = cryptoFile.size ?: Long.MAX_VALUE
						var decrypted: Long = 0
						var read: Int
						while (decryptingReadableByteChannel.read(buff).also { read = it } > 0) {
							buff.flip()
							data.write(buff.array(), 0, buff.remaining())
							decryptedTempFileOutputStream.write(buff.array(), 0, buff.remaining())

							decrypted += read.toLong()
							progressAware
								.onProgress(
									Progress.progress(DownloadState.decryption(cryptoFile)) //
										.between(0) //
										.and(cleartextSize) //
										.withValue(decrypted)
								)
						}
					}
				}
			} finally {
				decryptedTempFileOutputStream.flush()
				decryptedTempFileOutputStream.close()
				encryptedTmpFile.delete()
				progressAware.onProgress(Progress.completed(DownloadState.decryption(cryptoFile)))
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}

		// store it in cloud-related LRU cache
		if(genThumbnail) {
			generateAndStoreThumbnail(diskCache, cacheKey, decryptedTempFile)
		}
		decryptedTempFile.delete()
	}

	protected fun generateCacheKey(cloudFile: CloudFile) : String{
        var cacheKey = ""
        cloudFile.cloud?.id()?.let { cacheKey += it } // distinguish between two files with same path but on different instances of the same cloud
		cloudFile.path.hashCode().toString().let{ cacheKey += it }
		return cacheKey
	}

	private fun isGenerateThumbnailsEnabled(cache: DiskLruCache?, fileName: String) : Boolean {
		return 	sharedPreferencesHandler.useLruCache() &&
				sharedPreferencesHandler.generateThumbnails() != ThumbnailsOption.NEVER &&
				cache != null &&
				isImageMediaType(fileName)
	}

	private fun generateAndStoreThumbnail(cache: DiskLruCache?, cacheKey: String, thumbnailTmp: File) {
		// generate the Bitmap (in memory)
		val bitmap : Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ThumbnailUtils.createImageThumbnail(thumbnailTmp, Size(100, 100), null)
		} else {
			ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(thumbnailTmp.path), 100, 100)
		}

		// write the thumbnail in a file (on disk)
		val thumbnailFile : File = File.createTempFile(UUID.randomUUID().toString(), ".thumbnail", internalCache)
		bitmap.compress(Bitmap.CompressFormat.JPEG, 100, thumbnailFile.outputStream())

		try {
			cache?.let {
				// store File to LruCache (on disk)
				LruFileCacheUtil.storeToLruCache(it, cacheKey, thumbnailFile)
			} ?: Timber.tag("CryptoImplDecorator").e("Failed to store item in LRU cache")
		} catch (e: IOException) {
			Timber.tag("CryptoImplDecorator").e(e, "Failed to write the thumbnail in DiskLruCache")
		}

		thumbnailFile.delete()
	}

	protected fun isImageMediaType(filename: String): Boolean {
		return (mimeTypes.fromFilename(filename) ?: MimeType.WILDCARD_MIME_TYPE).mediatype == "image"
	}

	@Throws(BackendException::class, IOException::class)
	private fun readToTmpFile(cryptoFile: CryptoFile, file: CloudFile, progressAware: ProgressAware<DownloadState>): File {
		val encryptedTmpFile = File.createTempFile(UUID.randomUUID().toString(), ".crypto", internalCache)
		FileOutputStream(encryptedTmpFile).use { encryptedData ->
			cloudContentRepository.read(file, encryptedTmpFile, encryptedData, DownloadFileReplacingProgressAware(cryptoFile, progressAware))
			return encryptedTmpFile
		}
	}

	@Throws(BackendException::class)
	fun currentAccount(cloud: Cloud): String {
		return cloudContentRepository.checkAuthenticationAndRetrieveCurrentAccount(cloud)
	}

	@Throws(BackendException::class)
	fun getOrCreateCachingAwareDirIdInfo(folder: CryptoFolder): DirIdInfo {
		return dirIdCache[folder] ?: return getOrCreateDirIdInfo(folder)
	}

	@Throws(BackendException::class)
	fun getCachingAwareDirIdInfo(folder: CryptoFolder): DirIdInfo? {
		return dirIdCache[folder] ?: return getDirIdInfo(folder)
	}

	@Throws(BackendException::class)
	fun createDirIdInfoFor(dirId: String): DirIdInfo {
		val dirHash = dirHash(dirId)
		val lvl2Dir = lvl2Dir(dirHash)
		return DirIdInfo(dirId, lvl2Dir)
	}

	@Throws(BackendException::class, EmptyDirFileException::class)
	fun loadContentsOfDirFile(folder: CryptoFolder): ByteArray {
		folder.dirFile?.let {
			try {
				ByteArrayOutputStream().use { out ->
					cloudContentRepository.read(it, null, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
					if (dirfileIsEmpty(out)) {
						throw EmptyDirFileException(folder.name, folder.dirFile.path)
					}
					return out.toByteArray()
				}
			} catch (e: IOException) {
				throw FatalBackendException(e)
			}
		} ?: throw FatalBackendException("Dir file is null")
	}

	fun newDirId(): String {
		return UUID.randomUUID().toString()
	}

	fun dirfileIsEmpty(out: ByteArrayOutputStream): Boolean {
		return out.size() == 0
	}

	@Throws(BackendException::class)
	private fun lvl2Dir(dirHash: String): CloudFolder {
		return cloudContentRepository.folder(lvl1Dir(dirHash), dirHash.substring(2))
	}

	@Throws(BackendException::class)
	private fun lvl1Dir(dirHash: String): CloudFolder {
		return cloudContentRepository.folder(dataFolder(), dirHash.substring(0, 2))
	}

	fun cryptor(): Cryptor {
		return cryptor.get()
	}

	fun storageLocation(): CloudFolder {
		return storageLocation
	}

	fun addFolderToCache(result: CryptoFolder, dirInfo: DirIdInfo) {
		dirIdCache.put(result, dirInfo)
	}

	fun evictFromCache(cryptoFolder: CryptoFolder) {
		dirIdCache.evict(cryptoFolder)
	}

	@Throws(BackendException::class)
	fun writeShortNameFile(cryptoFile: CryptoFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, length: Long): CryptoFile {
		if (!replace) {
			assertCryptoFileAlreadyExists(cryptoFile)
		}
		try {
			data.open(context)?.use { stream ->
				requireNotNull(cryptoFile.size)
				val encryptedTmpFile = File.createTempFile(UUID.randomUUID().toString(), ".crypto", internalCache)
				try {
					Channels.newChannel(FileOutputStream(encryptedTmpFile)).use { writableByteChannel ->
						EncryptingWritableByteChannel(writableByteChannel, cryptor()).use { encryptingWritableByteChannel ->
							progressAware.onProgress(Progress.started(UploadState.encryption(cryptoFile)))
							val buff = ByteBuffer.allocate(cryptor().fileContentCryptor().cleartextChunkSize())
							val ciphertextSize = cryptor().fileContentCryptor().ciphertextSize(cryptoFile.size) + cryptor().fileHeaderCryptor().headerSize()
							var read: Int
							var encrypted: Long = 0
							while (stream.read(buff.array()).also { read = it } > 0) {
								buff.limit(read)
								val written = encryptingWritableByteChannel.write(buff)
								buff.flip()
								encrypted += written.toLong()
								progressAware.onProgress(Progress.progress(UploadState.encryption(cryptoFile)).between(0).and(ciphertextSize).withValue(encrypted))
							}
							encryptingWritableByteChannel.close()
							data.modifiedDate(context).ifPresent { encryptedTmpFile.setLastModified(it.time) }
							progressAware.onProgress(Progress.completed(UploadState.encryption(cryptoFile)))
							return writeFromTmpFile(data, cryptoFile, encryptedTmpFile, progressAware, replace)
						}
					}
				} finally {
					encryptedTmpFile.delete()
				}
			} ?: throw IllegalStateException("InputStream shouldn't be null")
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}
}
