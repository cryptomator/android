package org.cryptomator.util.crypto;

import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

import javax.crypto.SecretKey;

class CryptoOperationsFromApi23 implements CryptoOperations {

	@Override
	public Cipher cryptor(KeyStore keyStore, String alias) throws UnrecoverableStorageKeyException {
		try {
			final SecretKey key = (SecretKey) keyStore.getKey(alias, null);
			final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" //
					+ KeyProperties.BLOCK_MODE_CBC + "/" //
					+ KeyProperties.ENCRYPTION_PADDING_PKCS7);
			return new CipherFromApi23(cipher, key);
		} catch (UnrecoverableKeyException e) {
			throw new UnrecoverableStorageKeyException(e);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public KeyGenerator initializeKeyGenerator(Context context, final String alias) {
		final javax.crypto.KeyGenerator generator;
		try {
			generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreBuilder.DEFAULT_KEYSTORE_NAME);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return requireUserAuthentication -> {
			KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec //
					.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT) //
					.setBlockModes(KeyProperties.BLOCK_MODE_CBC) //
					.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);

			builder.setUserAuthenticationRequired(requireUserAuthentication);

			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
				builder.setInvalidatedByBiometricEnrollment(requireUserAuthentication);
			}
			generator.init(builder.build());
			generator.generateKey();
		};
	}
}
