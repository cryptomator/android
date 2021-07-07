package org.cryptomator.data.cloud.crypto

import android.content.Context
import com.google.common.io.BaseEncoding
import org.cryptomator.cryptolib.api.AuthenticationFailedException
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.common.EncryptingWritableByteChannel
import org.cryptomator.cryptolib.common.MessageDigestSupplier
import org.cryptomator.data.cloud.crypto.DirIdCache.DirIdInfo
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.EmptyDirFileException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoDirFileException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.exception.SymLinkException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.UploadFileReplacingProgressAware
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource.Companion.from
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.FileBasedDataSource.Companion.from
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.function.Supplier
import java.util.regex.Pattern
import kotlin.streams.toList
import timber.log.Timber

open class CryptoImplVaultFormat7 : CryptoImplDecorator {
	constructor(
		context: Context,
		cryptor: Supplier<Cryptor>,
		cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
		storageLocation: CloudFolder,
		dirIdCache: DirIdCache
	) : super(
		context, cryptor, cloudContentRepository, storageLocation, dirIdCache, CryptoConstants.DEFAULT_MAX_FILE_NAME
	)

	constructor(
		context: Context,
		cryptor: Supplier<Cryptor>,
		cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
		storageLocation: CloudFolder,
		dirIdCache: DirIdCache,
		shorteningThreshold: Int
	) : super(
		context, cryptor, cloudContentRepository, storageLocation, dirIdCache, shorteningThreshold
	)

	@Throws(BackendException::class)
	override fun folder(cryptoParent: CryptoFolder, cleartextName: String): CryptoFolder {
		val dirFileName = encryptFolderName(cryptoParent, cleartextName)
		val dirFolder = cloudContentRepository.folder(dirIdInfo(cryptoParent).cloudFolder, dirFileName)
		val dirFile = cloudContentRepository.file(dirFolder, CLOUD_FOLDER_DIR_FILE_PRE + CLOUD_NODE_EXT)
		return folder(cryptoParent, cleartextName, dirFile)
	}

	@Throws(BackendException::class)
	override fun encryptName(cryptoParent: CryptoFolder, name: String): String {
		var ciphertextName: String = cryptor() //
			.fileNameCryptor() //
			.encryptFilename(BaseEncoding.base64Url(), name, dirIdInfo(cryptoParent).id.toByteArray(StandardCharsets.UTF_8)) + CLOUD_NODE_EXT
		if (ciphertextName.length > shorteningThreshold) {
			ciphertextName = deflate(cryptoParent, ciphertextName)
		}
		return ciphertextName
	}

	@Throws(BackendException::class)
	private fun deflate(cryptoParent: CryptoFolder, longFileName: String): String {
		val longFilenameBytes = longFileName.toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortFileName = BaseEncoding.base64Url().encode(hash) + LONG_NODE_FILE_EXT
		var dirFolder = cloudContentRepository.folder(dirIdInfo(cryptoParent).cloudFolder, shortFileName)

		// if folder already exists in case of renaming
		if (!cloudContentRepository.exists(dirFolder)) {
			dirFolder = cloudContentRepository.create(dirFolder)
		}

		val data = longFileName.toByteArray(StandardCharsets.UTF_8)
		val cloudFile = cloudContentRepository.file(dirFolder, LONG_NODE_FILE_CONTENT_NAME + LONG_NODE_FILE_EXT, data.size.toLong())
		cloudContentRepository.write(cloudFile, from(data), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, true, data.size.toLong())
		return shortFileName
	}

	@Throws(BackendException::class)
	private fun metadataFile(cloudNode: CloudNode): CloudFile {
		val cloudFolder = when (cloudNode) {
			is CloudFile -> {
				cloudNode.parent
			}
			is CloudFolder -> {
				cloudNode
			}
			else -> {
				throw IllegalStateException("Should be file or folder")
			}
		}
		return cloudContentRepository.file(cloudFolder, LONG_NODE_FILE_CONTENT_NAME + LONG_NODE_FILE_EXT)
	}

	@Throws(BackendException::class)
	private fun inflate(cloudNode: CloudNode): String {
		val metadataFile = metadataFile(cloudNode)
		val out = ByteArrayOutputStream()
		cloudContentRepository.read(metadataFile, null, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		return String(out.toByteArray(), StandardCharsets.UTF_8)
	}

	override fun decryptName(dirId: String, encryptedName: String): String? {
		return extractEncryptedName(encryptedName)?.let {
			return cryptor().fileNameCryptor().decryptFilename(BaseEncoding.base64Url(), it, dirId.toByteArray(StandardCharsets.UTF_8))
		}
	}

	@Throws(BackendException::class)
	override fun list(cryptoFolder: CryptoFolder): List<CryptoNode> {
		dirIdCache.evictSubFoldersOf(cryptoFolder)

		val dirIdInfo = dirIdInfo(cryptoFolder)
		val dirId = dirIdInfo(cryptoFolder).id
		val lvl2Dir = dirIdInfo.cloudFolder

		val ciphertextNodes: List<CloudNode> = try {
			cloudContentRepository.list(lvl2Dir)
		} catch (e: NoSuchCloudFileException) {
			if (cryptoFolder is RootCryptoFolder) {
				Timber.tag("CryptoFs").e("No lvl2Dir exists for root folder in %s", lvl2Dir.path)
				throw FatalBackendException(String.format("No lvl2Dir exists for root folder in %s", lvl2Dir.path), e)
			} else if (cryptoFolder.dirFile == null) {
				Timber.tag("CryptoFs").e(String.format("Dir-file of folder is null %s", lvl2Dir.path))
				throw FatalBackendException(String.format("Dir-file of folder is null %s", lvl2Dir.path))
			} else if (cloudContentRepository.exists(cloudContentRepository.file(cryptoFolder.dirFile.parent, CLOUD_NODE_SYMLINK_PRE + CLOUD_NODE_EXT))) {
				throw SymLinkException()
			} else if (!cloudContentRepository.exists(cryptoFolder.dirFile)) {
				Timber.tag("CryptoFs").e("No dir file exists in %s", cryptoFolder.dirFile.path)
				throw NoDirFileException(cryptoFolder.name, cryptoFolder.dirFile.path)
			}
			return emptyList()
		}

		return ciphertextNodes
			.parallelStream()
			.map { node ->
				ciphertextToCleartextNode(cryptoFolder, dirId, node)
			}
			.toList()
			.filterNotNull()
	}

	@Throws(BackendException::class)
	private fun ciphertextToCleartextNode(cryptoFolder: CryptoFolder, dirId: String, cloudNode: CloudNode): CryptoNode? {
		var ciphertextName = cloudNode.name
		var longNameFolderDirFile: CloudFile? = null
		var longNameFile: CloudFile? = null

		if (ciphertextName.endsWith(CLOUD_NODE_EXT)) {
			ciphertextName = nameWithoutExtension(ciphertextName)
		} else if (ciphertextName.endsWith(LONG_NODE_FILE_EXT)) {
			ciphertextName = (longNodeCiphertextName(cloudNode) ?: return null)
			for (node in cloudContentRepository.list((cloudNode as CloudFolder))) {
				when (node.name) {
					LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT -> longNameFile = node as CloudFile
					CLOUD_FOLDER_DIR_FILE_PRE + CLOUD_NODE_EXT -> longNameFolderDirFile = node as CloudFile
				}
			}
		}
		return try {
			val cleartextName = decryptName(dirId, ciphertextName)
			if (cleartextName == null) {
				Timber.tag("CryptoFs").w("Failed to parse cipher text name of: %s", cloudNode.path)
				return null
			}
			cloudNodeFromName(cloudNode, cryptoFolder, cleartextName, longNameFile, longNameFolderDirFile)
		} catch (e: AuthenticationFailedException) {
			Timber.tag("CryptoFs").w(e, "File/Folder name authentication failed: %s", cloudNode.path)
			null
		} catch (e: IllegalArgumentException) {
			Timber.tag("CryptoFs").w(e, "Illegal ciphertext filename/folder: %s", cloudNode.path)
			null
		}
	}

	@Throws(BackendException::class)
	private fun cloudNodeFromName(cloudNode: CloudNode, cryptoFolder: CryptoFolder, cleartextName: String, longNameFile: CloudFile?, dirFile: CloudFile?): CryptoNode? {
		if (cloudNode is CloudFile) {
			val cleartextSize = cloudNode.size?.let {
				val ciphertextSizeWithoutHeader = it - cryptor().fileHeaderCryptor().headerSize()
				if (ciphertextSizeWithoutHeader >= 0) {
					cryptor().fileContentCryptor().cleartextSize(ciphertextSizeWithoutHeader)
				} else {
					null
				}
			}
			return file(cryptoFolder, cleartextName, cloudNode, cleartextSize)
		} else if (cloudNode is CloudFolder) {
			return if (longNameFile != null) {
				// long file
				val cleartextSize = longNameFile.size?.let {
					val ciphertextSizeWithoutHeader: Long = it - cryptor().fileHeaderCryptor().headerSize()
					if (ciphertextSizeWithoutHeader >= 0) {
						cryptor().fileContentCryptor().cleartextSize(ciphertextSizeWithoutHeader)
					} else {
						null
					}
				}

				file(cryptoFolder, cleartextName, longNameFile, cleartextSize)
			} else {
				// folder
				if (dirFile != null) {
					folder(cryptoFolder, cleartextName, dirFile)
				} else {
					val constructedDirFile = cloudContentRepository.file(cloudNode, "dir$CLOUD_NODE_EXT")
					folder(cryptoFolder, cleartextName, constructedDirFile)
				}
			}
		}
		return null
	}

	private fun longNodeCiphertextName(cloudNode: CloudNode): String? {
		return try {
			val ciphertextName = inflate(cloudNode)
			nameWithoutExtension(ciphertextName)
		} catch (e: NoSuchCloudFileException) {
			Timber.tag("CryptoFs").e("Missing %s%s for cloud node: %s", LONG_NODE_FILE_CONTENT_NAME, LONG_NODE_FILE_EXT, cloudNode.path)
			null
		} catch (e: BackendException) {
			Timber.tag("CryptoFs").e(e, "Failed to read %s%s for cloud node: %s", LONG_NODE_FILE_CONTENT_NAME, LONG_NODE_FILE_EXT, cloudNode.path)
			null
		}
	}

	@Throws(BackendException::class)
	override fun createDirIdInfo(folder: CryptoFolder): DirIdInfo {
		val dirId = loadDirId(folder)
		return dirIdCache.put(folder, createDirIdInfoFor(dirId))
	}

	@Throws(BackendException::class)
	override fun encryptFolderName(cryptoFolder: CryptoFolder, name: String): String {
		return encryptName(cryptoFolder, name)
	}

	@Throws(BackendException::class)
	override fun symlink(cryptoParent: CryptoFolder, cleartextName: String, target: String): CryptoSymlink {
		throw FatalBackendException("FOOOO") // FIXME
	}

	@Throws(BackendException::class, EmptyDirFileException::class)
	override fun loadDirId(folder: CryptoFolder): String {
		var dirFile: CloudFile? = null
		if (folder.dirFile != null) {
			dirFile = folder.dirFile
		}
		return if (RootCryptoFolder.isRoot(folder)) {
			CryptoConstants.ROOT_DIR_ID
		} else if (dirFile != null && cloudContentRepository.exists(dirFile)) {
			String(loadContentsOfDirFile(dirFile), StandardCharsets.UTF_8)
		} else {
			newDirId()
		}
	}

	@Throws(BackendException::class, EmptyDirFileException::class)
	private fun loadContentsOfDirFile(file: CloudFile): ByteArray {
		try {
			ByteArrayOutputStream().use { out ->
				cloudContentRepository.read(file, null, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
				if (dirfileIsEmpty(out)) {
					throw EmptyDirFileException(file.name, file.path)
				}
				return out.toByteArray()
			}
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	override fun create(folder: CryptoFolder): CryptoFolder {
		requireNotNull(folder.dirFile)
		var shortName = false
		if (folder.dirFile.parent.name.endsWith(LONG_NODE_FILE_EXT)) {
			assertCryptoLongDirFileAlreadyExists(folder)
		} else {
			assertCryptoFolderAlreadyExists(folder)
			shortName = true
		}
		val dirIdInfo = dirIdInfo(folder)
		val createdCloudFolder = cloudContentRepository.create(dirIdInfo.cloudFolder)
		var dirFolder = folder.dirFile.parent
		var dirFile = folder.dirFile
		if (shortName) {
			dirFolder = cloudContentRepository.create(dirFolder)
			dirFile = cloudContentRepository.file(dirFolder, folder.dirFile.name)
		}
		val dirId = dirIdInfo.id.toByteArray(StandardCharsets.UTF_8)
		val createdDirFile = cloudContentRepository.write(dirFile, from(dirId), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, dirId.size.toLong())
		val result = folder(folder, createdDirFile)
		addFolderToCache(result, dirIdInfo.withCloudFolder(createdCloudFolder))
		return result
	}

	override fun extractEncryptedName(ciphertextName: String): String? {
		val matcher = BASE64_ENCRYPTED_NAME_PATTERN.matcher(ciphertextName)
		return if (matcher.find(0)) {
			matcher.group()
		} else {
			null
		}
	}

	@Throws(BackendException::class)
	override fun move(source: CryptoFolder, target: CryptoFolder): CryptoFolder {
		requireNotNull(source.dirFile)
		requireNotNull(target.dirFile)
		target.parent?.let { targetsParent ->
			var shortName = false
			if (target.dirFile.parent.name.endsWith(LONG_NODE_FILE_EXT)) {
				assertCryptoLongDirFileAlreadyExists(target)
			} else {
				assertCryptoFolderAlreadyExists(target)
				shortName = true
			}
			var targetDirFile = target.dirFile
			if (shortName) {
				val targetDirFolder = cloudContentRepository.create(target.dirFile.parent)
				targetDirFile = cloudContentRepository.file(targetDirFolder, target.dirFile.name)
			}
			val result = folder(targetsParent, target.name, cloudContentRepository.move(source.dirFile, targetDirFile))
			cloudContentRepository.delete(source.dirFile.parent)
			evictFromCache(source)
			evictFromCache(target)
			return result
		} ?: throw ParentFolderIsNullException(target.name)
	}

	@Throws(BackendException::class)
	override fun move(source: CryptoFile, target: CryptoFile): CryptoFile {
		return if (source.cloudFile.parent.name.endsWith(LONG_NODE_FILE_EXT)) {
			val targetDirFolder = cloudContentRepository.folder(target.cloudFile.parent, target.cloudFile.name)
			val cryptoFile: CryptoFile = if (target.cloudFile.name.endsWith(LONG_NODE_FILE_EXT)) {
				assertCryptoLongDirFileAlreadyExists(targetDirFolder)
				moveLongFileToLongFile(source, target, targetDirFolder)
			} else {
				assertCryptoFileAlreadyExists(target)
				moveLongFileToShortFile(source, target)
			}
			source.cloudFile.parent.parent?.let {
				val sourceDirFolder = cloudContentRepository.folder(it, source.cloudFile.parent.name)
				cloudContentRepository.delete(sourceDirFolder)
			}
			cryptoFile
		} else {
			if (target.cloudFile.name.endsWith(LONG_NODE_FILE_EXT)) {
				val targetDirFolder = cloudContentRepository.folder(target.cloudFile.parent, target.cloudFile.name)
				assertCryptoLongDirFileAlreadyExists(targetDirFolder)
				moveShortFileToLongFile(source, target, targetDirFolder)
			} else {
				assertCryptoFileAlreadyExists(target)
				file(target, cloudContentRepository.move(source.cloudFile, target.cloudFile), source.size)
			}
		}
	}

	@Throws(BackendException::class)
	private fun moveLongFileToLongFile(source: CryptoFile, target: CryptoFile, targetDirFolder: CloudFolder): CryptoFile {
		requireNotNull(source.cloudFile.parent)
		val sourceFile = cloudContentRepository.file(source.cloudFile.parent, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT)
		val movedFile = cloudContentRepository.move(sourceFile, cloudContentRepository.file(targetDirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT))
		return file(target, movedFile, movedFile.size)
	}

	@Throws(BackendException::class)
	private fun moveLongFileToShortFile(source: CryptoFile, target: CryptoFile): CryptoFile {
		requireNotNull(source.cloudFile.parent)
		val sourceFile = cloudContentRepository.file(source.cloudFile.parent, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT)
		val movedFile = cloudContentRepository.move(sourceFile, target.cloudFile)
		return file(target, movedFile, movedFile.size)
	}

	@Throws(BackendException::class)
	private fun moveShortFileToLongFile(source: CryptoFile, target: CryptoFile, targetDirFolder: CloudFolder): CryptoFile {
		val movedFile = cloudContentRepository.move(source.cloudFile, cloudContentRepository.file(targetDirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT))
		return file(target, movedFile, movedFile.size)
	}

	@Throws(BackendException::class)
	override fun delete(node: CloudNode) {
		if (node is CryptoFolder) {
			requireNotNull(node.dirFile)
			val cryptoSubfolders = deepCollectSubfolders(node)
			for (cryptoSubfolder in cryptoSubfolders) {
				try {
					cloudContentRepository.delete(dirIdInfo(cryptoSubfolder).cloudFolder)
				} catch (e: NoSuchCloudFileException) {
					// Ignoring because nothing can be done if the dir-file doesn't exists in the cloud
				}
			}
			try {
				cloudContentRepository.delete(dirIdInfo(node).cloudFolder)
			} catch (e: NoSuchCloudFileException) {
				// Ignoring because nothing can be done if the dir-file doesn't exists in the cloud
			}
			cloudContentRepository.delete(node.dirFile.parent)
			evictFromCache(node)
		} else if (node is CryptoFile) {
			if (node.cloudFile.parent.name.endsWith(LONG_NODE_FILE_EXT)) {
				cloudContentRepository.delete(node.cloudFile.parent)
			} else {
				cloudContentRepository.delete(node.cloudFile)
			}
		}
	}

	@Throws(BackendException::class)
	override fun write(cryptoFile: CryptoFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, length: Long): CryptoFile {
		return if (cryptoFile.cloudFile.name.endsWith(LONG_NODE_FILE_EXT)) {
			writeLongFile(cryptoFile, data, progressAware, replace, length)
		} else {
			writeShortNameFile(cryptoFile, data, progressAware, replace, length)
		}
	}

	@Throws(BackendException::class)
	private fun writeLongFile(cryptoFile: CryptoFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, length: Long): CryptoFile {
		val dirFolder = cloudContentRepository.folder(cryptoFile.cloudFile.parent, cryptoFile.cloudFile.name)
		val cloudFile = cloudContentRepository.file(dirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT, data.size(context))
		assertCryptoLongDirFileAlreadyExists(dirFolder)
		try {
			data.open(context)?.use { stream ->
				val encryptedTmpFile = File.createTempFile(UUID.randomUUID().toString(), ".crypto", internalCache)
				try {
					Channels.newChannel(FileOutputStream(encryptedTmpFile)).use { writableByteChannel ->
						EncryptingWritableByteChannel(writableByteChannel, cryptor()).use { encryptingWritableByteChannel ->
							cloudFile.size?.let { size ->
								progressAware.onProgress(Progress.started(UploadState.encryption(cloudFile)))
								val buff = ByteBuffer.allocate(cryptor().fileContentCryptor().cleartextChunkSize())
								val ciphertextSize = cryptor().fileContentCryptor().ciphertextSize(size) + cryptor().fileHeaderCryptor().headerSize()
								var read: Int
								var encrypted: Long = 0
								while (stream.read(buff.array()).also { read = it } > 0) {
									buff.limit(read)
									val written = encryptingWritableByteChannel.write(buff)
									buff.flip()
									encrypted += written.toLong()
									progressAware.onProgress(Progress.progress(UploadState.encryption(cloudFile)).between(0).and(ciphertextSize).withValue(encrypted))
								}
								encryptingWritableByteChannel.close()
								progressAware.onProgress(Progress.completed(UploadState.encryption(cloudFile)))
								val targetFile = targetFile(cryptoFile, cloudFile, replace)
								return file(
									cryptoFile,  //
									cloudContentRepository.write( //
										targetFile,  //
										data.decorate(from(encryptedTmpFile)),
										UploadFileReplacingProgressAware(cryptoFile, progressAware),  //
										replace,  //
										encryptedTmpFile.length()
									),  //
									cryptoFile.size
								)
							} ?: throw FatalBackendException("CloudFile size shouldn't be null")
						}
					}
				} finally {
					encryptedTmpFile.delete()
				}
			} ?: throw FatalBackendException("InputStream shouldn't be null")
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	private fun targetFile(cryptoFile: CryptoFile, cloudFile: CloudFile, replace: Boolean): CloudFile {
		return if (replace || !cloudContentRepository.exists(cloudFile)) {
			cloudFile
		} else firstNonExistingAutoRenamedFile(cryptoFile)
	}

	@Throws(BackendException::class)
	private fun firstNonExistingAutoRenamedFile(original: CryptoFile): CloudFile {
		val name = original.name
		val nameWithoutExtension = nameWithoutExtension(name)
		var extension = extension(name)
		if (extension.isNotEmpty()) {
			extension = ".$extension"
		}
		var counter = 1
		var result: CryptoFile
		var cloudFile: CloudFile
		do {
			val newFileName = "$nameWithoutExtension ($counter)$extension"
			result = file(original.parent, newFileName, original.size)
			counter++
			val dirFolder = cloudContentRepository.folder(result.cloudFile.parent, result.cloudFile.name)
			cloudFile = cloudContentRepository.file(dirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT, result.size)
		} while (cloudContentRepository.exists(cloudFile))
		return cloudFile
	}

	@Throws(BackendException::class)
	private fun assertCryptoLongDirFileAlreadyExists(cryptoFolder: CloudFolder) {
		if (cloudContentRepository.exists(cloudContentRepository.file(cryptoFolder, CLOUD_FOLDER_DIR_FILE_PRE + CLOUD_NODE_EXT))) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}
	}

	companion object {

		private const val CLOUD_NODE_EXT = ".c9r"
		private const val LONG_NODE_FILE_EXT = ".c9s"
		private const val CLOUD_FOLDER_DIR_FILE_PRE = "dir"
		private const val LONG_NODE_FILE_CONTENT_CONTENTS = "contents"
		private const val LONG_NODE_FILE_CONTENT_NAME = "name"
		private const val CLOUD_NODE_SYMLINK_PRE = "symlink"
		private val BASE64_ENCRYPTED_NAME_PATTERN = Pattern.compile("^([A-Za-z0-9+/\\-_]{4})*([A-Za-z0-9+/\\-]{4}|[A-Za-z0-9+/\\-_]{3}=|[A-Za-z0-9+/\\-_]{2}==)?$")
	}
}
