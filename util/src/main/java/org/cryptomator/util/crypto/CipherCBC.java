package org.cryptomator.util.crypto;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

class CipherCBC extends BaseCipher {

	public static final int IV_LENGTH = 16;

	CipherCBC(javax.crypto.Cipher cipher, SecretKey key) {
		super(cipher, key, IV_LENGTH);
	}

	@Override
	protected IvParameterSpec getIvParameterSpec(byte[] iv) {
		return new IvParameterSpec(iv);
	}
}
