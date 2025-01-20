package org.cryptomator.data.cloud.crypto

import com.google.common.base.Optional
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.api.CryptorProvider
import org.cryptomator.cryptolib.api.InvalidPassphraseException
import org.cryptomator.cryptolib.api.Masterkey
import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException
import org.cryptomator.cryptolib.common.MasterkeyFileAccess
import org.cryptomator.data.cloud.crypto.VaultConfig.Companion.createVaultConfig
import org.cryptomator.data.cloud.crypto.VaultConfig.Companion.verify
import org.cryptomator.data.cloud.crypto.VaultConfig.VaultConfigBuilder
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CancellationException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.vaultconfig.MissingVaultConfigFileException
import org.cryptomator.domain.exception.vaultconfig.UnsupportedMasterkeyLocationException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource.Companion.from
import org.cryptomator.domain.usecases.cloud.Flag
import org.cryptomator.domain.usecases.vault.UnlockToken
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.text.Normalizer

class MasterkeyCryptoCloudProvider(
	private val cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,  //
	private val cryptoCloudContentRepositoryFactory: CryptoCloudContentRepositoryFactory,  //
	private val secureRandom: SecureRandom
) : CryptoCloudProvider {

	@Throws(BackendException::class)
	override fun create(location: CloudFolder, password: CharSequence) {
		// Just for testing (id in VaultConfig is auto generated which makes sense while creating a vault but not for testing)
		create(location, password, createVaultConfig())
	}

	// Visible for testing
	@Throws(BackendException::class)
	fun create(location: CloudFolder, password: CharSequence?, vaultConfigBuilder: VaultConfigBuilder) {
		// 1. write masterkey:
		val masterkey = Masterkey.generate(secureRandom)
		try {
			ByteArrayOutputStream().use { data ->
				MasterkeyFileAccess(CryptoConstants.PEPPER, secureRandom).persist(masterkey, data, password, CryptoConstants.DEFAULT_MASTERKEY_FILE_VERSION)
				cloudContentRepository.write(legacyMasterkeyFile(location), from(data.toByteArray()), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, data.size().toLong())
			}
		} catch (e: IOException) {
			throw FatalBackendException("Failed to write masterkey", e)
		}

		// 2. initialize vault:
		val vaultConfig = vaultConfigBuilder //
			.vaultFormat(CryptoConstants.MAX_VAULT_VERSION) //
			.cipherCombo(CryptoConstants.DEFAULT_CIPHER_COMBO) //
			.keyId(URI.create(String.format("%s:%s", CryptoConstants.MASTERKEY_SCHEME, CryptoConstants.MASTERKEY_FILE_NAME))) //
			.shorteningThreshold(CryptoConstants.DEFAULT_MAX_FILE_NAME) //
			.build()
		val encodedVaultConfig = vaultConfig.toToken(masterkey.encoded).toByteArray(StandardCharsets.UTF_8)
		val vaultFile = cloudContentRepository.file(location, CryptoConstants.VAULT_FILE_NAME)
		cloudContentRepository.write(vaultFile, from(encodedVaultConfig), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, encodedVaultConfig.size.toLong())

		// 3. create root folder:
		createRootFolder(location, cryptorFor(masterkey, vaultConfig.cipherCombo))
	}

	@Throws(BackendException::class)
	private fun createRootFolder(location: CloudFolder, cryptor: Cryptor) {
		var dFolder = cloudContentRepository.folder(location, CryptoConstants.DATA_DIR_NAME)
		dFolder = cloudContentRepository.create(dFolder)
		val rootDirHash = cryptor.fileNameCryptor().hashDirectoryId(CryptoConstants.ROOT_DIR_ID)
		var lvl1Folder = cloudContentRepository.folder(dFolder, rootDirHash.substring(0, 2))
		lvl1Folder = cloudContentRepository.create(lvl1Folder)
		val lvl2Folder = cloudContentRepository.folder(lvl1Folder, rootDirHash.substring(2))
		cloudContentRepository.create(lvl2Folder)
	}

	@Throws(BackendException::class)
	override fun unlock(vault: Vault, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, password: CharSequence, cancelledFlag: Flag): Vault {
		return unlock(createUnlockToken(vault, unverifiedVaultConfig), unverifiedVaultConfig, password, cancelledFlag)
	}

	@Throws(BackendException::class)
	override fun unlock(token: UnlockToken, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, password: CharSequence, cancelledFlag: Flag): Vault {
		val impl = token as UnlockTokenImpl
		return try {
			val masterkey = impl.getKeyFile(password)
			val vaultFormat: Int
			val shorteningThreshold: Int
			val cryptor: Cryptor
			if (unverifiedVaultConfig.isPresent) {
				val vaultConfig = verify(masterkey.encoded, unverifiedVaultConfig.get())
				vaultFormat = vaultConfig.vaultFormat
				assertVaultVersionIsSupported(vaultConfig.vaultFormat)
				shorteningThreshold = vaultConfig.shorteningThreshold
				cryptor = cryptorFor(masterkey, vaultConfig.cipherCombo)
			} else {
				vaultFormat = MasterkeyFileAccess.readAllegedVaultVersion(impl.keyFileData)
				assertLegacyVaultVersionIsSupported(vaultFormat)
				shorteningThreshold = if (vaultFormat > 6) CryptoConstants.DEFAULT_MAX_FILE_NAME else CryptoImplVaultFormatPre7.SHORTENING_THRESHOLD
				cryptor = cryptorFor(masterkey, CryptorProvider.Scheme.SIV_CTRMAC)
			}
			if (cancelledFlag.get()) {
				throw CancellationException()
			}
			val vault = Vault.aCopyOf(token.vault) //
				.withUnlocked(true) //
				.withFormat(vaultFormat) //
				.withShorteningThreshold(shorteningThreshold) //
				.build()
			cryptoCloudContentRepositoryFactory.registerCryptor(vault, cryptor)
			vault
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	override fun unlock(vault: Vault, unverifiedVaultConfig: UnverifiedVaultConfig, vaultKeyJwe: String, userKeyJwe: String, cancelledFlag: Flag): Vault {
		throw UnsupportedOperationException("Password based vaults do not support hub unlock")
	}

	@Throws(BackendException::class)
	override fun createUnlockToken(vault: Vault, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>): UnlockTokenImpl {
		val vaultLocation = vaultLocation(vault)
		return if (unverifiedVaultConfig.isPresent) {
			createUnlockToken(vault, masterkeyFile(vaultLocation, unverifiedVaultConfig.get()))
		} else {
			createUnlockToken(vault, legacyMasterkeyFile(vaultLocation))
		}
	}

	@Throws(BackendException::class)
	private fun masterkeyFile(vaultLocation: CloudFolder, unverifiedVaultConfig: UnverifiedVaultConfig): CloudFile {
		val path = unverifiedVaultConfig.keyId.schemeSpecificPart
		if (path != CryptoConstants.MASTERKEY_FILE_NAME) {
			throw UnsupportedMasterkeyLocationException(unverifiedVaultConfig)
		}
		return cloudContentRepository.file(vaultLocation, path)
	}

	@Throws(BackendException::class)
	private fun legacyMasterkeyFile(location: CloudFolder): CloudFile {
		return cloudContentRepository.file(location, CryptoConstants.MASTERKEY_FILE_NAME)
	}

	@Throws(BackendException::class)
	private fun createUnlockToken(vault: Vault, location: CloudFile): UnlockTokenImpl {
		val keyFileData = readKeyFileData(location)
		return UnlockTokenImpl(vault, secureRandom, keyFileData)
	}

	@Throws(BackendException::class)
	private fun readKeyFileData(masterkeyFile: CloudFile): ByteArray {
		val data = ByteArrayOutputStream()
		cloudContentRepository.read(masterkeyFile, null, data, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		return data.toByteArray()
	}

	// Visible for testing
	fun cryptorFor(keyFile: Masterkey?, vaultCipherCombo: CryptorProvider.Scheme): Cryptor {
		return CryptorProvider.forScheme(vaultCipherCombo).provide(keyFile, secureRandom)
	}

	@Throws(BackendException::class)
	override fun isVaultPasswordValid(vault: Vault, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, password: CharSequence): Boolean {
		return try {
			// create a cryptor, which checks the password, then destroy it immediately
			val unlockToken = createUnlockToken(vault, unverifiedVaultConfig)
			val masterkey = unlockToken.getKeyFile(password)
			val vaultCipherCombo = if (unverifiedVaultConfig.isPresent) {
				val vaultConfig = verify(masterkey.encoded, unverifiedVaultConfig.get())
				assertVaultVersionIsSupported(vaultConfig.vaultFormat)
				vaultConfig.cipherCombo
			} else {
				val vaultVersion = MasterkeyFileAccess.readAllegedVaultVersion(unlockToken.keyFileData)
				assertLegacyVaultVersionIsSupported(vaultVersion)
				CryptorProvider.Scheme.SIV_CTRMAC
			}
			cryptorFor(masterkey, vaultCipherCombo).destroy()
			true
		} catch (e: InvalidPassphraseException) {
			false
		} catch (e: IOException) {
			throw FatalBackendException(e)
		}
	}

	override fun lock(vault: Vault) {
		cryptoCloudContentRepositoryFactory.deregisterCryptor(vault)
	}

	private fun assertVaultVersionIsSupported(version: Int) {
		if (version < CryptoConstants.MIN_VAULT_VERSION) {
			throw UnsupportedVaultFormatException(version, CryptoConstants.MIN_VAULT_VERSION)
		} else if (version > CryptoConstants.MAX_VAULT_VERSION) {
			throw UnsupportedVaultFormatException(version, CryptoConstants.MAX_VAULT_VERSION)
		}
	}

	private fun assertLegacyVaultVersionIsSupported(version: Int) {
		when {
			version < CryptoConstants.MIN_VAULT_VERSION -> {
				throw UnsupportedVaultFormatException(version, CryptoConstants.MIN_VAULT_VERSION)
			}
			version == CryptoConstants.DEFAULT_MASTERKEY_FILE_VERSION -> {
				throw MissingVaultConfigFileException()
			}
			version > CryptoConstants.MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG -> {
				throw UnsupportedVaultFormatException(version, CryptoConstants.MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG)
			}
		}
	}

	@Throws(BackendException::class)
	override fun changePassword(vault: Vault, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, oldPassword: String, newPassword: String) {
		val vaultLocation = vaultLocation(vault)
		val masterkeyFile = if (unverifiedVaultConfig.isPresent) {
			masterkeyFile(vaultLocation, unverifiedVaultConfig.get())
		} else {
			legacyMasterkeyFile(vaultLocation)
		}
		val dataOutputStream = ByteArrayOutputStream()
		cloudContentRepository.read(masterkeyFile, null, dataOutputStream, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD)
		val data = dataOutputStream.toByteArray()
		val vaultVersion: Int
		if (unverifiedVaultConfig.isPresent) {
			vaultVersion = unverifiedVaultConfig.get().vaultFormat
			assertVaultVersionIsSupported(vaultVersion)
		} else {
			try {
				vaultVersion = MasterkeyFileAccess.readAllegedVaultVersion(data)
				assertLegacyVaultVersionIsSupported(vaultVersion)
			} catch (e: IOException) {
				throw FatalBackendException("Failed to read legacy vault version", e)
			}
		}
		createBackupMasterKeyFile(data, masterkeyFile)
		createNewMasterKeyFile(data, vaultVersion, oldPassword, newPassword, masterkeyFile)
	}

	@Throws(BackendException::class)
	private fun vaultLocation(vault: Vault): CloudFolder {
		return cloudContentRepository.resolve(vault.cloud, vault.path)
	}

	@Throws(BackendException::class)
	private fun createBackupMasterKeyFile(data: ByteArray, masterkeyFile: CloudFile) {
		cloudContentRepository.write(masterkeyBackupFile(masterkeyFile, data), from(data), ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, true, data.size.toLong())
	}

	@Throws(BackendException::class)
	private fun masterkeyBackupFile(masterkeyFile: CloudFile, data: ByteArray): CloudFile {
		val fileName = masterkeyFile.name + BackupFileIdSuffixGenerator.generate(data) + CryptoConstants.MASTERKEY_BACKUP_FILE_EXT
		return cloudContentRepository.file(masterkeyFile.parent, fileName)
	}

	@Throws(BackendException::class)
	private fun createNewMasterKeyFile(data: ByteArray, vaultVersion: Int, oldPassword: String, newPassword: String, masterkeyFile: CloudFile) {
		try {
			val newMasterKeyFile = MasterkeyFileAccess(CryptoConstants.PEPPER, secureRandom) //
				.changePassphrase(data, normalizePassword(oldPassword, vaultVersion), normalizePassword(newPassword, vaultVersion))
			cloudContentRepository.write(
				masterkeyFile,  //
				from(newMasterKeyFile),  //
				ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD,  //
				true,  //
				newMasterKeyFile.size.toLong()
			)
		} catch (e: IOException) {
			throw FatalBackendException("Failed to read legacy vault version", e)
		}
	}

	private fun normalizePassword(password: CharSequence, vaultVersion: Int): CharSequence {
		return if (vaultVersion >= CryptoConstants.VERSION_WITH_NORMALIZED_PASSWORDS) {
			Normalizer.normalize(password, Normalizer.Form.NFC)
		} else {
			password
		}
	}

	class UnlockTokenImpl(private val vault: Vault, private val secureRandom: SecureRandom, val keyFileData: ByteArray) : UnlockToken {

		override fun getVault(): Vault {
			return vault
		}

		@Throws(IOException::class)
		fun getKeyFile(password: CharSequence?): Masterkey {
			return MasterkeyFileAccess(CryptoConstants.PEPPER, secureRandom).load(ByteArrayInputStream(keyFileData), password)
		}
	}
}
