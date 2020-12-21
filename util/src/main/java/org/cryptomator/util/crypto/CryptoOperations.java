package org.cryptomator.util.crypto;

import android.content.Context;

import java.security.KeyStore;

interface CryptoOperations {

	KeyGenerator initializeKeyGenerator(Context context, String alias);

	Cipher cryptor(KeyStore keyStore, String alias);

}
