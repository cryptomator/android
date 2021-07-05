package org.cryptomator.util.crypto;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

public class CredentialCryptor {

	private static final String DEFAULT_KEY_ALIAS = "cryptoKeyAccessToken";

	private final Cipher cipher;

	private CredentialCryptor(Context context) {
		KeyStore keyStore = KeyStoreBuilder.defaultKeyStore() //
				.withKey(DEFAULT_KEY_ALIAS, false, context) //
				.build();
		this.cipher = CryptoOperationsFactory //
				.cryptoOperations() //
				.cryptor(keyStore, DEFAULT_KEY_ALIAS);
	}

	public static CredentialCryptor getInstance(Context context) {
		return new CredentialCryptor(context);
	}

	public byte[] encrypt(byte[] decrypted) {
		return cipher.encrypt(decrypted);
	}

	public String encrypt(String decrypted) {
		return new String(encrypt(decrypted.getBytes(StandardCharsets.UTF_8)), StandardCharsets.ISO_8859_1);
	}

	public byte[] decrypt(byte[] encrypted) {
		return cipher.decrypt(encrypted);
	}

	public String decrypt(String encrypted) {
		return new String(decrypt(encrypted.getBytes(StandardCharsets.ISO_8859_1)), StandardCharsets.UTF_8);
	}

}
