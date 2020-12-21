package org.cryptomator.util.crypto;

import android.content.Context;

import java.security.KeyStore;

import static org.cryptomator.util.Encodings.ISO_8859_1;
import static org.cryptomator.util.Encodings.UTF_8;

public class CredentialCryptor {

	private static final String DEFAULT_KEY_ALIAS = "cryptoKeyAccessToken";

	private final Cipher cipher;

	public static CredentialCryptor getInstance(Context context) {
		return new CredentialCryptor(context);
	}

	private CredentialCryptor(Context context) {
		KeyStore keyStore = KeyStoreBuilder.defaultKeyStore() //
				.withKey(DEFAULT_KEY_ALIAS, false, context) //
				.build();
		this.cipher = CryptoOperationsFactory //
				.cryptoOperations() //
				.cryptor(keyStore, DEFAULT_KEY_ALIAS);
	}

	public byte[] encrypt(byte[] decrypted) {
		return cipher.encrypt(decrypted);
	}

	public String encrypt(String decrypted) {
		return new String(encrypt(decrypted.getBytes(UTF_8)), ISO_8859_1);
	}

	public byte[] decrypt(byte[] encrypted) {
		return cipher.decrypt(encrypted);
	}

	public String decrypt(String encrypted) {
		return new String(decrypt(encrypted.getBytes(ISO_8859_1)), UTF_8);
	}

}
