package org.cryptomator.util.crypto;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;

import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

class CryptoOperationsGCM implements CryptoOperations {

	private static final int KEY_SIZE = 256;
	private static final String ENCRYPTION_BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM;
	private static final String ENCRYPTION_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE;

	@Override
	public Cipher cryptor(KeyStore keyStore, String alias) throws UnrecoverableStorageKeyException {
		try {
			final SecretKey key = (SecretKey) keyStore.getKey(alias, null);
			final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(ENCRYPTION_ALGORITHM + "/" //
					+ ENCRYPTION_BLOCK_MODE + "/" //
					+ ENCRYPTION_PADDING);
			return new CipherGCM(cipher, key);
		} catch (UnrecoverableKeyException e) {
			throw new UnrecoverableStorageKeyException(e);
		} catch (NoSuchPaddingException | NoSuchAlgorithmException | KeyStoreException e) {
			throw new FatalCryptoException(e);
		}
	}

	@Override
	public KeyGenerator initializeKeyGenerator(Context context, final String alias) {
		final javax.crypto.KeyGenerator generator;
		try {
			generator = javax.crypto.KeyGenerator.getInstance(ENCRYPTION_ALGORITHM, ANDROID_KEYSTORE);
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			throw new FatalCryptoException(e);
		}
		return requireUserAuthentication -> {
			KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec //
					.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT) //
					.setBlockModes(ENCRYPTION_BLOCK_MODE) //
					.setEncryptionPaddings(ENCRYPTION_PADDING) //
					.setKeySize(KEY_SIZE) //
					.setUserAuthenticationRequired(requireUserAuthentication) //
					.setInvalidatedByBiometricEnrollment(requireUserAuthentication);

			generator.init(builder.build());
			generator.generateKey();
		};
	}
}
