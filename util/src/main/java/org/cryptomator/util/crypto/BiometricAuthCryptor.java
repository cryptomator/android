package org.cryptomator.util.crypto;

import android.content.Context;

import org.cryptomator.util.ByteArrayUtils;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

public class BiometricAuthCryptor {

	private static final String BIOMETRIC_AUTH_KEY_ALIAS = "fingerprintCryptoKeyAccessToken";

	private final Cipher cipher;
	private final CryptoMode cryptoMode;

	private BiometricAuthCryptor(Context context, CryptoMode cryptoMode) throws UnrecoverableStorageKeyException {
		String suffixedAlias = getSuffixedAlias(cryptoMode);
		KeyStore keyStore = KeyStoreBuilder.defaultKeyStore() //
				.withKey(suffixedAlias, true, cryptoMode, context) //
				.build();
		this.cryptoMode = cryptoMode;
		this.cipher = CryptoOperationsFactory.cryptoOperations(cryptoMode).cryptor(keyStore, suffixedAlias);
	}

	private static String getSuffixedAlias(CryptoMode cryptoMode) {
		return switch (cryptoMode) {
			case CBC -> BIOMETRIC_AUTH_KEY_ALIAS; // CBC does not have an alias due to legacy reasons
			case GCM -> BIOMETRIC_AUTH_KEY_ALIAS + "_GCM";
			case NONE -> throw new IllegalStateException("CryptoMode.NONE is not allowed here");
		};
	}

	public static BiometricAuthCryptor getInstance(Context context, CryptoMode cryptoMode) throws UnrecoverableStorageKeyException {
		return new BiometricAuthCryptor(context, cryptoMode);
	}

	public static void recreateKey(Context context, CryptoMode cryptoMode) {
		KeyStoreBuilder.defaultKeyStore() //
				.withRecreatedKey(getSuffixedAlias(cryptoMode), true, cryptoMode, context) //
				.build();
	}

	public javax.crypto.Cipher getDecryptCipher(String decrypted) throws InvalidAlgorithmParameterException, InvalidKeyException {
		return cipher.getDecryptCipher(decrypted.getBytes(StandardCharsets.ISO_8859_1));
	}

	public javax.crypto.Cipher getEncryptCipher() throws InvalidKeyException {
		return cipher.getEncryptCipher();
	}

	public String encrypt(javax.crypto.Cipher cipher, String password) throws IllegalBlockSizeException, BadPaddingException {
		byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
		byte[] encryptedPasswordAndIv = ByteArrayUtils.join(cipher.getIV(), encrypted);
		return new String(encryptedPasswordAndIv, StandardCharsets.ISO_8859_1);
	}

	public String decrypt(javax.crypto.Cipher cipher, String password) throws IllegalBlockSizeException, BadPaddingException {
		int ivLength = switch (cryptoMode) {
			case CBC -> CipherCBC.IV_LENGTH;
			case GCM -> CipherGCM.IV_LENGTH;
			case NONE -> throw new IllegalStateException("CryptoMode.NONE is not allowed here");
		};
		byte[] ciphered = cipher.doFinal(CryptoByteArrayUtils.getBytes(password.getBytes(StandardCharsets.ISO_8859_1), ivLength));
		return new String(ciphered, StandardCharsets.UTF_8);
	}
}
