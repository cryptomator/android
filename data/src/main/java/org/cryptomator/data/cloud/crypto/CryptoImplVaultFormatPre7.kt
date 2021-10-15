package org.cryptomator.data.cloud.crypto

import android.content.Context
import com.google.common.io.BaseEncoding
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.BaseNCodec
import org.cryptomator.cryptolib.api.AuthenticationFailedException
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.common.MessageDigestSupplier
import org.cryptomator.data.cloud.crypto.DirIdCache.DirIdInfo
import org.cryptomator.data.cloud.crypto.RootCryptoFolder.Companion.isRoot
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.exception.AlreadyExistException
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.EmptyDirFileException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource.Companion.from
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.function.Supplier
import java.util.regex.Pattern
import kotlin.streams.toList
import timber.log.Timber

internal class CryptoImplVaultFormatPre7(
	context: Context,
	cryptor: Supplier<Cryptor>,
	cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
	storageLocation: CloudFolder,
	dirIdCache: DirIdCache
) :
	CryptoImplDecorator(
		context, cryptor, cloudContentRepository, storageLocation, dirIdCache, SHORTENING_THRESHOLD
	) {

	@Throws(BackendException::class)
	override fun folder(cryptoParent: CryptoFolder, cleartextName: String): CryptoFolder {
		val dirFileName = encryptFolderName(cryptoParent, cleartextName)
		val dirFile = cloudContentRepository.file(dirIdInfo(cryptoParent).cloudFolder, dirFileName)
		return folder(cryptoParent, cleartextName, dirFile)
	}

	@Throws(BackendException::class)
	override fun create(folder: CryptoFolder): CryptoFolder {
		requireNotNull(folder.dirFile)
		assertCryptoFolderAlreadyExists(folder)
		val dirIdInfo = dirIdInfo(folder)
		val createdCloudFolder = cloudContentRepository.create(dirIdInfo.cloudFolder)
		val dirId = dirIdInfo.id.toByteArray(StandardCharsets.UTF_8)
		val createdDirFile = cloudContentRepository.write(folder.dirFile, from(dirId), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, dirId.size.toLong())
		return folder(folder, createdDirFile).also {
			addFolderToCache(it, dirIdInfo.withCloudFolder(createdCloudFolder))
		}
	}

	@Throws(BackendException::class)
	override fun encryptName(cryptoParent: CryptoFolder, name: String): String {
		return encryptName(cryptoParent, name, "")
	}

	@Throws(BackendException::class)
	private fun encryptName(cryptoParent: CryptoFolder, name: String, prefix: String): String {
		var ciphertextName = prefix + cryptor().fileNameCryptor().encryptFilename(BaseEncoding.base32(), name, dirIdInfo(cryptoParent).id.toByteArray(StandardCharsets.UTF_8))
		if (ciphertextName.length > shorteningThreshold) {
			ciphertextName = deflate(ciphertextName)
		}
		return ciphertextName
	}

	@Throws(BackendException::class)
	private fun deflate(longFileName: String): String {
		val longFilenameBytes = longFileName.toByteArray(StandardCharsets.UTF_8)
		val hash = MessageDigestSupplier.SHA1.get().digest(longFilenameBytes)
		val shortFileName = BASE32.encodeAsString(hash) + LONG_NAME_FILE_EXT
		val metadataFile = metadataFile(shortFileName)
		val data = longFileName.toByteArray(StandardCharsets.UTF_8)
		try {
			cloudContentRepository.create(metadataFile.parent)
		} catch (e: AlreadyExistException) {
		}
		cloudContentRepository.write(metadataFile, from(data), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, true, data.size.toLong())
		return shortFileName
	}

	@Throws(BackendException::class)
	private fun inflate(shortFileName: String): String {
		val metadataFile = metadataFile(shortFileName)
		val out = ByteArrayOutputStream()
		cloudContentRepository.read(metadataFile, null, out, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		return String(out.toByteArray(), StandardCharsets.UTF_8)
	}

	@Throws(BackendException::class)
	private fun inflatePermanently(cloudFile: CloudFile, longFileName: String): CloudFile {
		Timber.tag("CryptoFs").i("inflatePermanently: %s -> %s", cloudFile.name, longFileName)
		val newCiphertextFile = cloudContentRepository.file(cloudFile.parent, longFileName)
		cloudContentRepository.move(cloudFile, newCiphertextFile)
		return newCiphertextFile
	}

	@Throws(BackendException::class)
	private fun metadataFile(shortFilename: String): CloudFile {
		val firstLevelFolder = cloudContentRepository.folder(metadataFolder(), shortFilename.substring(0, 2))
		val secondLevelFolder = cloudContentRepository.folder(firstLevelFolder, shortFilename.substring(2, 4))
		return cloudContentRepository.file(secondLevelFolder, shortFilename)
	}

	@Throws(BackendException::class)
	private fun metadataFolder(): CloudFolder {
		return cloudContentRepository.folder(storageLocation(), METADATA_DIR_NAME)
	}

	@Throws(BackendException::class)
	override fun list(cryptoFolder: CryptoFolder): List<CryptoNode> {
		val dirIdInfo = dirIdInfo(cryptoFolder)
		val dirId = dirIdInfo(cryptoFolder).id
		val lvl2Dir = dirIdInfo.cloudFolder
		return cloudContentRepository
			.list(lvl2Dir)
			.filterIsInstance<CloudFile>()
			.parallelStream()
			.map { node ->
				ciphertextToCleartextNode(cryptoFolder, dirId, node)
			}
			.toList()
			.filterNotNull()
	}

	@Throws(BackendException::class)
	private fun ciphertextToCleartextNode(cryptoFolder: CryptoFolder, dirId: String, cloudNode: CloudFile): CryptoNode? {
		var cloudFile = cloudNode
		var ciphertextName = cloudFile.name
		if (ciphertextName.endsWith(LONG_NAME_FILE_EXT)) {
			try {
				ciphertextName = inflate(ciphertextName)
				if (ciphertextName.length <= shorteningThreshold) {
					cloudFile = inflatePermanently(cloudFile, ciphertextName)
				}
			} catch (e: NoSuchCloudFileException) {
				Timber.tag("CryptoFs").e("Missing mFile: %s", ciphertextName)
				return null
			} catch (e: BackendException) {
				Timber.tag("CryptoFs").e(e, "Failed to read mFile: %s", ciphertextName)
				return null
			}
		}
		val cleartextName: String? = try {
			decryptName(dirId, ciphertextName.uppercase())
		} catch (e: AuthenticationFailedException) {
			Timber.tag("CryptoFs").w("File name authentication failed: %s", cloudFile.path)
			return null
		} catch (e: IllegalArgumentException) {
			Timber.tag("CryptoFs").d("Illegal ciphertext filename: %s", cloudFile.path)
			return null
		}
		return if (cleartextName == null || ciphertextName.startsWith(SYMLINK_PREFIX)) {
			null
		} else if (ciphertextName.startsWith(DIR_PREFIX)) {
			folder(cryptoFolder, cleartextName, cloudFile)
		} else {
			val cleartextSize = cloudFile.size?.let {
				val ciphertextSizeWithoutHeader: Long = it - cryptor().fileHeaderCryptor().headerSize()
				if (ciphertextSizeWithoutHeader >= 0) {
					cryptor().fileContentCryptor().cleartextSize(ciphertextSizeWithoutHeader)
				} else {
					null
				}
			}
			file(cryptoFolder, cleartextName, cloudFile, cleartextSize)
		}
	}

	override fun decryptName(dirId: String, encryptedName: String): String? {
		val ciphertextName = extractEncryptedName(encryptedName)
		return if (ciphertextName != null) {
			cryptor().fileNameCryptor().decryptFilename(BaseEncoding.base32(), ciphertextName, dirId.toByteArray(StandardCharsets.UTF_8))
		} else {
			null
		}
	}

	override fun extractEncryptedName(ciphertextName: String): String? {
		val matcher = BASE32_ENCRYPTED_NAME_PATTERN.matcher(ciphertextName)
		return if (matcher.find(0)) {
			matcher.group(2)
		} else {
			null
		}
	}

	@Throws(BackendException::class)
	override fun symlink(cryptoParent: CryptoFolder, cleartextName: String, target: String): CryptoSymlink {
		val ciphertextName = encryptSymlinkName(cryptoParent, cleartextName)
		val cloudFile = cloudContentRepository.file(dirIdInfo(cryptoParent).cloudFolder, ciphertextName)
		return CryptoSymlink(cryptoParent, cleartextName, path(cryptoParent, cleartextName), target, cloudFile)
	}

	@Throws(BackendException::class)
	private fun encryptSymlinkName(cryptoFolder: CryptoFolder, name: String): String {
		return encryptName(cryptoFolder, name, SYMLINK_PREFIX)
	}

	@Throws(BackendException::class)
	override fun encryptFolderName(cryptoFolder: CryptoFolder, name: String): String {
		return encryptName(cryptoFolder, name, DIR_PREFIX)
	}

	@Throws(BackendException::class)
	override fun move(source: CryptoFolder, target: CryptoFolder): CryptoFolder {
		requireNotNull(source.dirFile)
		requireNotNull(target.dirFile)
		target.parent?.let {
			assertCryptoFolderAlreadyExists(target)
			return folder(it, target.name, cloudContentRepository.move(source.dirFile, target.dirFile)).also {
				evictFromCache(source)
				evictFromCache(target)
			}
		} ?: throw ParentFolderIsNullException(target.name)
	}

	@Throws(BackendException::class)
	override fun move(source: CryptoFile, target: CryptoFile): CryptoFile {
		assertCryptoFileAlreadyExists(target)
		return file(target, cloudContentRepository.move(source.cloudFile, target.cloudFile), source.size)
	}

	@Throws(BackendException::class)
	override fun delete(node: CloudNode) {
		if (node is CryptoFolder) {
			requireNotNull(node.dirFile)
			val cryptoSubfolders = deepCollectSubfolders(node)
			for (cryptoSubfolder in cryptoSubfolders) {
				cloudContentRepository.delete(dirIdInfo(cryptoSubfolder).cloudFolder)
			}
			cloudContentRepository.delete(dirIdInfo(node).cloudFolder)
			cloudContentRepository.delete(node.dirFile)
			evictFromCache(node)
		} else if (node is CryptoFile) {
			cloudContentRepository.delete(node.cloudFile)
		}
	}

	@Throws(BackendException::class, EmptyDirFileException::class)
	override fun loadDirId(folder: CryptoFolder): String {
		return if (isRoot(folder)) {
			CryptoConstants.ROOT_DIR_ID
		} else if (folder.dirFile != null && cloudContentRepository.exists(folder.dirFile)) {
			String(loadContentsOfDirFile(folder), StandardCharsets.UTF_8)
		} else {
			newDirId()
		}
	}

	@Throws(BackendException::class)
	override fun createDirIdInfo(folder: CryptoFolder): DirIdInfo {
		val dirId = loadDirId(folder)
		return dirIdCache.put(folder, createDirIdInfoFor(dirId))
	}

	@Throws(BackendException::class)
	override fun write(cryptoFile: CryptoFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, length: Long): CryptoFile {
		return writeShortNameFile(cryptoFile, data, progressAware, replace, length)
	}

	companion object {

		const val SHORTENING_THRESHOLD = 129
		private const val DIR_PREFIX = "0"
		private const val SYMLINK_PREFIX = "1S"
		private const val LONG_NAME_FILE_EXT = ".lng"
		private const val METADATA_DIR_NAME = "m"
		private val BASE32: BaseNCodec = Base32()
		private val BASE32_ENCRYPTED_NAME_PATTERN = Pattern.compile("^(0|1S)?(([A-Z2-7]{8})*[A-Z2-7=]{8})$")
	}
}
