package org.cryptomator.util.crypto;

import android.content.Context;

import org.cryptomator.util.ByteArrayUtils;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;

import static org.cryptomator.util.Encodings.ISO_8859_1;
import static org.cryptomator.util.Encodings.UTF_8;

public class BiometricAuthCryptor {

	private static final String BIOMETRIC_AUTH_KEY_ALIAS = "fingerprintCryptoKeyAccessToken";

	private final Cipher cipher;

	public static BiometricAuthCryptor getInstance(Context context) throws UnrecoverableStorageKeyException {
		return new BiometricAuthCryptor(context);
	}

	private BiometricAuthCryptor(Context context) throws UnrecoverableStorageKeyException {
		KeyStore keyStore = KeyStoreBuilder.defaultKeyStore() //
				.withKey(BIOMETRIC_AUTH_KEY_ALIAS, true, context) //
				.build();
		this.cipher = CryptoOperationsFactory.cryptoOperations().cryptor(keyStore, BIOMETRIC_AUTH_KEY_ALIAS);
	}

	public javax.crypto.Cipher getDecryptCipher(String decrypted) throws InvalidAlgorithmParameterException, InvalidKeyException {
		return cipher.getDecryptCipher(decrypted.getBytes(ISO_8859_1));
	}

	public javax.crypto.Cipher getEncryptCipher() throws InvalidKeyException {
		return cipher.getEncryptCipher();
	}

	public String encrypt(javax.crypto.Cipher cipher, String password) throws IllegalBlockSizeException, BadPaddingException {
		byte[] encrypted = cipher.doFinal(password.getBytes(UTF_8));
		byte[] encryptedPasswordAndIv = ByteArrayUtils.join(cipher.getIV(), encrypted);
		return new String(encryptedPasswordAndIv, ISO_8859_1);
	}

	public String decrypt(javax.crypto.Cipher cipher, String password) throws IllegalBlockSizeException, BadPaddingException {
		byte[] ciphered = cipher.doFinal(CipherFromApi23.getBytes(password.getBytes(ISO_8859_1)));
		return new String(ciphered, UTF_8);
	}

	public static void recreateKey(Context context) {
		KeyStoreBuilder.defaultKeyStore() //
				.withRecreatedKey(BIOMETRIC_AUTH_KEY_ALIAS, true, context) //
				.build();
	}
}
