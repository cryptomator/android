package org.cryptomator.data.cloud.crypto

import com.google.common.base.Optional
import com.nimbusds.jose.JWEObject
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.api.CryptorProvider
import org.cryptomator.cryptolib.api.Masterkey
import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException
import org.cryptomator.data.cloud.crypto.VaultConfig.Companion.verify
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CancellationException
import org.cryptomator.domain.usecases.cloud.Flag
import org.cryptomator.domain.usecases.vault.UnlockToken
import org.cryptomator.util.crypto.HubDeviceCryptor
import java.security.SecureRandom

class HubkeyCryptoCloudProvider(
	private val cryptoCloudContentRepositoryFactory: CryptoCloudContentRepositoryFactory,  //
	private val secureRandom: SecureRandom
) : CryptoCloudProvider {

	@Throws(BackendException::class)
	override fun create(location: CloudFolder, password: CharSequence) {
		throw IllegalStateException("Hub can not create vaults from within the app")
	}

	@Throws(BackendException::class)
	override fun unlock(vault: Vault, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, password: CharSequence, cancelledFlag: Flag): Vault {
		throw IllegalStateException("Hub can not unlock vaults using password")
	}

	@Throws(BackendException::class)
	override fun unlock(token: UnlockToken, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, password: CharSequence, cancelledFlag: Flag): Vault {
		throw IllegalStateException("Hub can not unlock vaults using password")
	}

	override fun unlock(vault: Vault, unverifiedVaultConfig: UnverifiedVaultConfig, vaultKeyJwe: String, userKeyJwe: String, cancelledFlag: Flag): Vault {
		val vaultKey = JWEObject.parse(vaultKeyJwe)
		val userKey = JWEObject.parse(userKeyJwe)
		val masterkey = HubDeviceCryptor.getInstance().decryptVaultKey(vaultKey, userKey)
		val vaultConfig = verify(masterkey.encoded, unverifiedVaultConfig)
		val vaultFormat = vaultConfig.vaultFormat
		assertVaultVersionIsSupported(vaultConfig.vaultFormat)
		val shorteningThreshold = vaultConfig.shorteningThreshold
		val cryptor = cryptorFor(masterkey, vaultConfig.cipherCombo)
		if (cancelledFlag.get()) {
			throw CancellationException()
		}
		val unlockedVault = Vault.aCopyOf(vault) //
			.withUnlocked(true) //
			.withFormat(vaultFormat) //
			.withShorteningThreshold(shorteningThreshold) //
			.build()
		cryptoCloudContentRepositoryFactory.registerCryptor(unlockedVault, cryptor)
		return unlockedVault
	}

	@Throws(BackendException::class)
	override fun createUnlockToken(vault: Vault, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>): UnlockTokenImpl {
		throw IllegalStateException("Hub can not unlock vaults using password")
	}

	// Visible for testing
	fun cryptorFor(keyFile: Masterkey, vaultCipherCombo: CryptorProvider.Scheme): Cryptor {
		return CryptorProvider.forScheme(vaultCipherCombo).provide(keyFile, secureRandom)
	}

	@Throws(BackendException::class)
	override fun isVaultPasswordValid(vault: Vault, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, password: CharSequence): Boolean {
		throw IllegalStateException("Hub can not unlock vaults using password")
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

	@Throws(BackendException::class)
	override fun changePassword(vault: Vault, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, oldPassword: String, newPassword: String) {
		throw IllegalStateException("Hub can not unlock vaults using password")
	}

	class UnlockTokenImpl(private val vault: Vault) : UnlockToken {

		override fun getVault(): Vault {
			return vault
		}
	}
}
