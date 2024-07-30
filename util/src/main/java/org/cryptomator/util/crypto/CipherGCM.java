package org.cryptomator.util.crypto;

import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

class CipherGCM extends BaseCipher {

	public static final int IV_LENGTH = 12;

	CipherGCM(javax.crypto.Cipher cipher, SecretKey key) {
		super(cipher, key, IV_LENGTH);
	}

	@Override
	protected AlgorithmParameterSpec getIvParameterSpec(byte[] iv) {
		return new GCMParameterSpec(128, iv);
	}
}
