package org.cryptomator.data.cloud.crypto;

public class CryptoConstants {

	public static final String MASTERKEY_SCHEME = "masterkeyfile";

	static final String MASTERKEY_FILE_NAME = "masterkey.cryptomator";

	static final String ROOT_DIR_ID = "";
	static final String DATA_DIR_NAME = "d";
	static final String VAULT_FILE_NAME = "vault.cryptomator";
	static final String MASTERKEY_BACKUP_FILE_EXT = ".bkup";

	static final int DEFAULT_MASTERKEY_FILE_VERSION = 999;
	static final int MAX_VAULT_VERSION = 8;
	static final int MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG = 7;
	static final int VERSION_WITH_NORMALIZED_PASSWORDS = 6;
	static final int MIN_VAULT_VERSION = 5;

	static final int DEFAULT_MAX_FILE_NAME = 220;

	static final byte[] PEPPER = new byte[0];

	static final VaultCipherCombo DEFAULT_CIPHER_COMBO = VaultCipherCombo.SIV_CTRMAC;

}
