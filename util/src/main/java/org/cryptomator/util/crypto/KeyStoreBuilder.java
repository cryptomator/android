package org.cryptomator.util.crypto;

import android.content.Context;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class KeyStoreBuilder {

	static final String DEFAULT_KEYSTORE_NAME = "AndroidKeyStore";

	public static DefaultKeyStoreBuilder defaultKeyStore() {
		return new KeyStoreBuilderImpl(initializeDefaultKeyStore());
	}

	private static KeyStore initializeKeyStore() {
		try {
			KeyStore keyStore = KeyStore.getInstance(DEFAULT_KEYSTORE_NAME);
			keyStore.load(null);
			return keyStore;
		} catch (IOException //
				| KeyStoreException //
				| CertificateException //
				| NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}
	}

	private static KeyStore initializeDefaultKeyStore() {
		return initializeKeyStore();
	}

	private KeyStoreBuilder() {
	}

	public interface CustomKeyStoreBuilder {

		KeyStore build();

	}

	public interface DefaultKeyStoreBuilder extends CustomKeyStoreBuilder {

		DefaultKeyStoreBuilder withKey(String alias, boolean requireUserAuthentication, Context context);

		CustomKeyStoreBuilder withRecreatedKey(String alias, boolean requireUserAuthentication, Context context);
	}

	private static class KeyStoreBuilderImpl implements KeyStoreBuilder.CustomKeyStoreBuilder, KeyStoreBuilder.DefaultKeyStoreBuilder {

		private KeyStore keyStore;

		private KeyStoreBuilderImpl(KeyStore keyStore) {
			this.keyStore = keyStore;
		}

		public KeyStoreBuilderImpl withKey(String alias, boolean requireUserAuthentication, Context context) {
			try {
				if (!doesKeyExist(alias)) {
					CryptoOperationsFactory.cryptoOperations() //
							.initializeKeyGenerator(context, alias) //
							.createKey(requireUserAuthentication);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return this;
		}

		public CustomKeyStoreBuilder withRecreatedKey(String alias, boolean requireUserAuthentication, Context context) {
			try {
				keyStore.deleteEntry(alias);

				CryptoOperationsFactory.cryptoOperations() //
						.initializeKeyGenerator(context, alias) //
						.createKey(requireUserAuthentication);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return this;
		}

		public KeyStore build() {
			return keyStore;
		}

		private boolean doesKeyExist(String alias) {
			boolean isKeyStoreCertAliasExisted;
			try {
				keyStore = KeyStore.getInstance(DEFAULT_KEYSTORE_NAME);
				keyStore.load(null);
				isKeyStoreCertAliasExisted = keyStore.containsAlias(alias);
			} catch (KeyStoreException //
					| CertificateException //
					| NoSuchAlgorithmException //
					| IOException e) {
				throw new RuntimeException(e);
			}

			return isKeyStoreCertAliasExisted;
		}

	}
}
