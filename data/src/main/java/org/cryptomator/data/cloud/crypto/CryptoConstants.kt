package org.cryptomator.data.cloud.crypto

import org.cryptomator.cryptolib.api.CryptorProvider

object CryptoConstants {

	const val MASTERKEY_SCHEME = "masterkeyfile"
	const val MASTERKEY_FILE_NAME = "masterkey.cryptomator"
	const val ROOT_DIR_ID = ""
	const val DATA_DIR_NAME = "d"
	const val VAULT_FILE_NAME = "vault.cryptomator"
	const val MASTERKEY_BACKUP_FILE_EXT = ".bkup"
	const val DEFAULT_MASTERKEY_FILE_VERSION = 999
	const val MAX_VAULT_VERSION = 8
	const val MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG = 7
	const val VERSION_WITH_NORMALIZED_PASSWORDS = 6
	const val MIN_VAULT_VERSION = 5
	const val DEFAULT_MAX_FILE_NAME = 220
	val PEPPER = ByteArray(0)
	val DEFAULT_CIPHER_COMBO = CryptorProvider.Scheme.SIV_CTRMAC

}
