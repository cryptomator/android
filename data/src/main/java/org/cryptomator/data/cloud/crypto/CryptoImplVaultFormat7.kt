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
		val dirFolder = cloudContentRepository.folder(getOrCreateCachingAwareDirIdInfo(cryptoParent).cloudFolder, dirFileName)
		val dirFile = cloudContentRepository.file(dirFolder, CLOUD_FOLDER_DIR_FILE_PRE + CLOUD_NODE_EXT)
		return folder(cryptoParent, cleartextName, dirFile)
	}

	@Throws(BackendException::class)
	override fun encryptName(cryptoParent: CryptoFolder, name: String): String {
		var ciphertextName: String = cryptor() //
			.fileNameCryptor() //
			.encryptFilename(BaseEncoding.base64Url(), name, getOrCreateCachingAwareDirIdInfo(cryptoParent).id.toByteArray(StandardCharsets.UTF_8)) + CLOUD_NODE_EXT
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
		var dirFolder = cloudContentRepository.folder(getOrCreateCachingAwareDirIdInfo(cryptoParent).cloudFolder, shortFileName)

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
	private fun inflate(cloudNode: CloudNode): String {
		val metadataFile = metadataFile(cloudNode)
		val out = ByteArrayOutputStream()
		cloudContentRepository.read(metadataFile, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		return String(out.toByteArray(), StandardCharsets.UTF_8)
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

	override fun decryptName(dirId: String, encryptedName: String): String? {
		return extractEncryptedName(encryptedName)?.let {
			return cryptor().fileNameCryptor().decryptFilename(BaseEncoding.base64Url(), it, dirId.toByteArray(StandardCharsets.UTF_8))
		}
	}

	@Throws(BackendException::class)
	override fun list(cryptoFolder: CryptoFolder): List<CryptoNode> {
		dirIdCache.evictSubFoldersOf(cryptoFolder)

		val dirIdInfo = getCachingAwareDirIdInfo(cryptoFolder)
			?: when (cryptoFolder.dirFile) {
				null -> {
					Timber.tag("CryptoFs").e(String.format("Dir-file of folder is null %s", cryptoFolder.path))
					throw FatalBackendException(String.format("Dir-file of folder is null %s", cryptoFolder.path))
				}
				else -> {
					Timber.tag("CryptoFs").e("No dir file exists in %s", cryptoFolder.dirFile.path)
					throw NoDirFileException(cryptoFolder.name, cryptoFolder.dirFile.path)
				}
			}

		val dirId = dirIdInfo.id
		val lvl2Dir = dirIdInfo.cloudFolder

		return try {
			cloudContentRepository.list(lvl2Dir)
		} catch (e: NoSuchCloudFileException) {
			when {
				cryptoFolder is RootCryptoFolder -> {
					Timber.tag("CryptoFs").e("No lvl2Dir exists for root folder in %s", lvl2Dir.path)
					throw FatalBackendException(String.format("No lvl2Dir exists for root folder in %s", lvl2Dir.path), e)
				}
				cryptoFolder.dirFile == null -> {
					Timber.tag("CryptoFs").e(String.format("Dir-file of folder is null %s", lvl2Dir.path))
					throw FatalBackendException(String.format("Dir-file of folder is null %s", lvl2Dir.path))
				}
				cloudContentRepository.exists(cloudContentRepository.file(cryptoFolder.dirFile.parent, CLOUD_NODE_SYMLINK_PRE + CLOUD_NODE_EXT)) -> {
					throw SymLinkException()
				}
				else -> return emptyList()
			}
		}.map { node ->
			ciphertextToCleartextNode(cryptoFolder, dirId, node)
		}.toList().filterNotNull()
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
			return file(cryptoFolder, cleartextName, cloudNode, cleartextSize(cloudNode))
		} else if (cloudNode is CloudFolder) {
			return if (longNameFile != null) {
				// long file
				file(cryptoFolder, cleartextName, longNameFile, cleartextSize(longNameFile))
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
	override fun getOrCreateDirIdInfo(folder: CryptoFolder): DirIdInfo {
		val dirId = loadDirId(folder) ?: newDirId()
		return dirIdCache.put(folder, createDirIdInfoFor(dirId))
	}

	override fun getDirIdInfo(folder: CryptoFolder): DirIdInfo? {
		return loadDirId(folder)?.let {
			dirIdCache.put(folder, createDirIdInfoFor(it))
		}
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
	override fun loadDirId(folder: CryptoFolder): String? {
		return if (RootCryptoFolder.isRoot(folder)) {
			CryptoConstants.ROOT_DIR_ID
		} else if (folder.dirFile != null && cloudContentRepository.exists(folder.dirFile)) {
			String(loadContentsOfDirFile(folder.dirFile), StandardCharsets.UTF_8)
		} else {
			null
		}
	}

	@Throws(BackendException::class, EmptyDirFileException::class)
	private fun loadContentsOfDirFile(file: CloudFile): ByteArray {
		try {
			ByteArrayOutputStream().use { out ->
				cloudContentRepository.read(file, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
				if (dirfileIsEmpty(out)) {
					throw EmptyDirFileException(file.parent.name, file.path)
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
		var leftOverDirFolder: CloudFolder? = null
		if (folder.dirFile.parent.name.endsWith(LONG_NODE_FILE_EXT)) {
			assertNoNodeHasShortenedName(folder.dirFile.parent, folder.name)
		} else {
			leftOverDirFolder = assertCryptoFolderAlreadyExistsUnlessLeftOver(folder)
			shortName = true
		}
		return withDirIdInfoOfNewFolder(folder) { dirIdInfo ->
			val dirId = dirIdInfo.id.toByteArray(StandardCharsets.UTF_8)
			// encrypted first, so that a vault locked meanwhile fails the creation before it changes the cloud
			val encryptedDirId = encryptedBackupDirId(dirId)
			val createdCloudFolder = createLvl2Dir(dirIdInfo)
			var dirFolder = folder.dirFile.parent
			var dirFile = folder.dirFile
			if (shortName) {
				dirFolder = leftOverDirFolder ?: createDirFolder(dirFolder)
				dirFile = cloudContentRepository.file(dirFolder, folder.dirFile.name)
			}
			val createdDirFile = try {
				cloudContentRepository.write(dirFile, from(dirId), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, dirId.size.toLong())
			} catch (e: Exception) {
				if (shortName) {
					deleteDirFolderOfFailedCreation(dirFolder)
				}
				throw e
			}
			val result = folder(folder, createdDirFile)
			addFolderToCache(result, dirIdInfo.withCloudFolder(createdCloudFolder))
			silentlyUploadBackupDirIdFile(encryptedDirId, createdCloudFolder)
			result
		}
	}

	/**
	 * Deletes [dirFolder] after putting the dir file into it failed, since a listing would show it as a folder that can't
	 * be opened. A creation or move of a folder made it, or took it over as left by an earlier one. It stays if it holds
	 * the dir file after all, e.g. when only the response was lost. It also stays if it holds another node, e.g. another
	 * device's symlink that a cloud creating folders over existing ones, like S3, let the creation take. If the deletion
	 * fails, e.g. while offline, a later creation takes the folder over.
	 */
	private fun deleteDirFolderOfFailedCreation(dirFolder: CloudFolder) {
		try {
			if (isLeftOverByFailedCreation(dirFolder)) {
				cloudContentRepository.delete(dirFolder)
			}
		} catch (e: Exception) {
			Timber.tag("CryptoFs").w(e, "Deleting the folder of a dir file that couldn't be written failed")
		}
	}

	/**
	 * Fails if a node has the name of [folder], unless that is the folder of its dir file, left by a creation that failed,
	 * see [isLeftOverByFailedCreation]. That folder is returned then, so that it is taken rather than created.
	 */
	@Throws(BackendException::class)
	private fun assertCryptoFolderAlreadyExistsUnlessLeftOver(folder: CryptoFolder): CloudFolder? {
		val dirFile = requireNotNull(folder.dirFile)
		val parent = requireNotNull(folder.parent)
		if (cloudContentRepository.exists(dirFile)) {
			throw CloudNodeAlreadyExistsException(folder.name)
		}
		if (!cloudContentRepository.exists(file(parent, folder.name))) {
			return null
		}
		if (!isLeftOverByFailedCreation(dirFile.parent)) {
			throw CloudNodeAlreadyExistsException(folder.name)
		}
		return dirFile.parent
	}

	/**
	 * Creates the folder that the dir file of a folder belongs into, or takes it over if a creation that failed left it. A
	 * cloud that tells files and folders apart by their type doesn't report that folder as a taken name beforehand.
	 */
	@Throws(BackendException::class)
	private fun createDirFolder(dirFolder: CloudFolder): CloudFolder {
		return try {
			cloudContentRepository.create(dirFolder)
		} catch (e: CloudNodeAlreadyExistsException) {
			if (isLeftOverByFailedCreation(dirFolder)) dirFolder else throw e
		}
	}

	/**
	 * Whether [dirFolder], the folder that the dir file of a folder belongs into, holds neither a dir file nor what makes
	 * it a symlink or a file with a shortened name, as a creation that failed before it wrote the dir file leaves it.
	 */
	@Throws(BackendException::class)
	private fun isLeftOverByFailedCreation(dirFolder: CloudFolder): Boolean {
		return isFolderHoldingNone(dirFolder, NODE_FILE_NAMES)
	}

	@Throws(BackendException::class)
	private fun isFolderHoldingNone(dirFolder: CloudFolder, nodeFileNames: Set<String>): Boolean {
		// asked first, since a folder looked up below one that isn't there can't be listed. exists() finds a folder at the
		// path of a file, too, so the listing of the parent tells which it is
		return cloudContentRepository.exists(dirFolder) //
				&& cloudContentRepository.list(requireNotNull(dirFolder.parent)).any { it is CloudFolder && it.name == dirFolder.name } //
				&& cloudContentRepository.list(dirFolder).none { it.name in nodeFileNames }
	}

	/**
	 * Fails if a folder, a symlink or a file has the shortened name whose folder is [dirFolder]. That folder itself is
	 * there for every node with a shortened name, since encrypting the name creates it.
	 */
	@Throws(BackendException::class)
	private fun assertNoNodeHasShortenedName(dirFolder: CloudFolder, name: String) {
		if (cloudContentRepository.list(dirFolder).any { it.name in NODE_FILE_NAMES }) {
			throw CloudNodeAlreadyExistsException(name)
		}
	}

	private fun encryptedBackupDirId(dirId: ByteArray): ByteArray {
		val data = ByteArrayOutputStream()
		Channels.newChannel(data).use { channel ->
			EncryptingWritableByteChannel(channel, cryptor()).use { encryptingChannel ->
				encryptingChannel.write(ByteBuffer.wrap(dirId))
			}
		}
		return data.toByteArray()
	}

	private fun silentlyUploadBackupDirIdFile(encryptedDirId: ByteArray, dirFolder: CloudFolder) {
		try {
			val dirIdBackupFile = cloudContentRepository.file(dirFolder, CryptoConstants.DIR_ID_FILE)
			cloudContentRepository.write(dirIdBackupFile, from(encryptedDirId), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, encryptedDirId.size.toLong())
		} catch (e: BackendException) {
			Timber.tag("CryptoFs").e(e, "Failed to create DirIdBackupFile")
		}
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
			var leftOverDirFolder: CloudFolder? = null
			if (target.dirFile.parent.name.endsWith(LONG_NODE_FILE_EXT)) {
				assertNoNodeHasShortenedName(target.dirFile.parent, target.name)
			} else {
				leftOverDirFolder = assertCryptoFolderAlreadyExistsUnlessLeftOver(target)
				shortName = true
			}
			var targetDirFile = target.dirFile
			if (shortName) {
				val targetDirFolder = leftOverDirFolder ?: createDirFolder(target.dirFile.parent)
				targetDirFile = cloudContentRepository.file(targetDirFolder, target.dirFile.name)
			}
			val movedDirFile = try {
				cloudContentRepository.move(source.dirFile, targetDirFile)
			} catch (e: Exception) {
				if (shortName) {
					deleteDirFolderOfFailedCreation(targetDirFile.parent)
				}
				throw e
			}
			val result = folder(targetsParent, target.name, movedDirFile)
			cloudContentRepository.delete(source.dirFile.parent)
			evictFromCache(source)
			evictFromCache(target)
			return result
		} ?: throw ParentFolderIsNullException(target.name)
	}

	@Throws(BackendException::class)
	override fun move(source: CryptoFile, target: CryptoFile): CryptoFile {
		val sourceDirFolder = longFileDirFolder(source)
		return if (sourceDirFolder != null) {
			val sourceFile = cloudContentRepository.file(sourceDirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT)
			val targetDirFolder = cloudContentRepository.folder(target.cloudFile.parent, target.cloudFile.name)
			val cryptoFile: CryptoFile = if (target.cloudFile.name.endsWith(LONG_NODE_FILE_EXT)) {
				assertCryptoLongDirFileAlreadyExists(targetDirFolder)
				moveLongFileToLongFile(sourceFile, target, targetDirFolder)
			} else {
				assertCryptoFileAlreadyExists(target)
				moveLongFileToShortFile(sourceFile, target)
			}
			cloudContentRepository.delete(sourceDirFolder)
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
	private fun moveLongFileToLongFile(sourceFile: CloudFile, target: CryptoFile, targetDirFolder: CloudFolder): CryptoFile {
		val movedFile = cloudContentRepository.move(sourceFile, cloudContentRepository.file(targetDirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT))
		return file(target, movedFile, movedFile.size)
	}

	@Throws(BackendException::class)
	private fun moveLongFileToShortFile(sourceFile: CloudFile, target: CryptoFile): CryptoFile {
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
			if (!cloudContentRepository.exists(node.dirFile)) {
				// a symlink, or what a creation that failed before it wrote the dir file left, which links no content
				if (!isFolderHoldingNone(node.dirFile.parent, FILE_NODE_FILE_NAMES)) {
					throw NoSuchCloudFileException(node.name)
				}
				cloudContentRepository.delete(node.dirFile.parent)
				evictFromCache(node)
				return
			}
			val cryptoSubfolders = deepCollectSubfolders(node)
			for (cryptoSubfolder in cryptoSubfolders) {
				getCachingAwareDirIdInfo(cryptoSubfolder)?.let {
					cloudContentRepository.delete(it.cloudFolder)
				} ?: Timber.tag("CryptoFs").w("Dir file doesn't exists of a sub folder while deleting the parent, continue anyway")
			}
			getCachingAwareDirIdInfo(node)?.let {
				cloudContentRepository.delete(it.cloudFolder)
			} ?: Timber.tag("CryptoFs").w("Dir file doesn't exists while deleting the folder, continue anyway")
			cloudContentRepository.delete(node.dirFile.parent)
			evictFromCache(node)
		} else if (node is CryptoFile) {
			cloudContentRepository.delete(longFileDirFolder(node) ?: node.cloudFile)
		}
	}

	/**
	 * A file whose name is shortened is the contents file inside its .c9s folder.
	 */
	@Throws(BackendException::class)
	override fun exists(file: CryptoFile): Boolean {
		val dirFolder = longFileDirFolder(file) ?: return super.exists(file)
		return cloudContentRepository.exists(cloudContentRepository.file(dirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT))
	}

	/**
	 * Like [exists].
	 */
	@Throws(BackendException::class)
	override fun metadata(file: CryptoFile): CryptoFile? {
		val dirFolder = longFileDirFolder(file) ?: return super.metadata(file)
		val contentsFile = cloudContentRepository.file(dirFolder, LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT)
		return cloudContentRepository.metadata(contentsFile)?.let { file(file, it, cleartextSize(it)) }
	}

	/**
	 * The .c9s folder of a file whose name is shortened. [file] is either the contents file inside it, as a listing
	 * returns it, or the folder itself, as `file(parent, name)` returns it. `null` for a file whose name isn't shortened.
	 */
	@Throws(BackendException::class)
	private fun longFileDirFolder(file: CryptoFile): CloudFolder? {
		val cloudFile = file.cloudFile
		return when {
			cloudFile.parent.name.endsWith(LONG_NODE_FILE_EXT) -> cloudFile.parent
			cloudFile.name.endsWith(LONG_NODE_FILE_EXT) -> cloudContentRepository.folder(cloudFile.parent, cloudFile.name)
			else -> null
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
								data.modifiedDate(context).ifPresent { encryptedTmpFile.setLastModified(it.time) }
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
		private val FILE_NODE_FILE_NAMES = setOf(LONG_NODE_FILE_CONTENT_CONTENTS + CLOUD_NODE_EXT)
		private val NODE_FILE_NAMES = FILE_NODE_FILE_NAMES + setOf(CLOUD_FOLDER_DIR_FILE_PRE + CLOUD_NODE_EXT, CLOUD_NODE_SYMLINK_PRE + CLOUD_NODE_EXT)
		private val BASE64_ENCRYPTED_NAME_PATTERN = Pattern.compile("^([A-Za-z0-9+/\\-_]{4})*([A-Za-z0-9+/\\-]{4}|[A-Za-z0-9+/\\-_]{3}=|[A-Za-z0-9+/\\-_]{2}==)?$")
	}
}
