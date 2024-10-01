package org.cryptomator.util.crypto;

import android.content.Context;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

interface CryptoOperations {

	String ANDROID_KEYSTORE = KeyStoreBuilder.DEFAULT_KEYSTORE_NAME;
	String ENCRYPTION_ALGORITHM = KeyProperties.KEY_ALGORITHM_AES;

	KeyGenerator initializeKeyGenerator(Context context, String alias);

	Cipher cryptor(KeyStore keyStore, String alias);

}
