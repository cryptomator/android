package org.cryptomator.util.crypto;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

public class CredentialCryptor {

	private static final String DEFAULT_KEY_ALIAS = "cryptoKeyAccessToken";

	private final Cipher cipher;

	private static String getSuffixedAlias(CryptoMode cryptoMode) {
		return switch (cryptoMode) {
			case CBC -> DEFAULT_KEY_ALIAS; // CBC does not have an alias due to legacy reasons
			case GCM -> DEFAULT_KEY_ALIAS + "_GCM";
			case NONE -> throw new IllegalStateException("CryptoMode.NONE is not allowed here");
		};
	}

	private CredentialCryptor(Context context, CryptoMode cryptoMode) {
		String suffixedAlias = getSuffixedAlias(cryptoMode);
		KeyStore keyStore = KeyStoreBuilder.defaultKeyStore() //
				.withKey(suffixedAlias, false, cryptoMode, context) //
				.build();
		this.cipher = CryptoOperationsFactory //
				.cryptoOperations(cryptoMode) //
				.cryptor(keyStore, suffixedAlias);
	}

	public static CredentialCryptor getInstance(Context context) {
		return new CredentialCryptor(context, CryptoMode.GCM);
	}

	public static CredentialCryptor getInstance(Context context, CryptoMode cryptoMode) {
		return new CredentialCryptor(context, cryptoMode);
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
