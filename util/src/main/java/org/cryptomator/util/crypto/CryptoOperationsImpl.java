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

class CryptoOperationsImpl implements CryptoOperations {

	@Override
	public Cipher cryptor(KeyStore keyStore, String alias) throws UnrecoverableStorageKeyException {
		try {
			final SecretKey key = (SecretKey) keyStore.getKey(alias, null);
			final javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" //
					+ KeyProperties.BLOCK_MODE_CBC + "/" //
					+ KeyProperties.ENCRYPTION_PADDING_PKCS7);
			return new CipherImpl(cipher, key);
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
			generator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KeyStoreBuilder.DEFAULT_KEYSTORE_NAME);
		} catch (NoSuchAlgorithmException | NoSuchProviderException e) {
			throw new FatalCryptoException(e);
		}
		return requireUserAuthentication -> {
			KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec //
					.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT) //
					.setBlockModes(KeyProperties.BLOCK_MODE_CBC) //
					.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7) //
					.setUserAuthenticationRequired(requireUserAuthentication) //
					.setInvalidatedByBiometricEnrollment(requireUserAuthentication);

			generator.init(builder.build());
			generator.generateKey();
		};
	}
}
