package org.cryptomator.util.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import static java.lang.System.arraycopy;

class CipherImpl implements Cipher {

	private static final int IV_LENGTH = 16;

	private final javax.crypto.Cipher cipher;
	private final SecretKey key;

	CipherImpl(javax.crypto.Cipher cipher, SecretKey key) {
		this.cipher = cipher;
		this.key = key;
	}

	private static byte[] mergeIvAndEncryptedData(byte[] encrypted, byte[] iv) {
		byte[] mergedIvAndEncrypted = new byte[encrypted.length + iv.length];
		arraycopy( //
				iv, 0, //
				mergedIvAndEncrypted, 0, IV_LENGTH);
		arraycopy( //
				encrypted, 0, //
				mergedIvAndEncrypted, IV_LENGTH, encrypted.length);
		return mergedIvAndEncrypted;
	}

	static byte[] getBytes(byte[] encryptedBytesWithIv) {
		byte[] bytes = new byte[encryptedBytesWithIv.length - IV_LENGTH];
		arraycopy( //
				encryptedBytesWithIv, IV_LENGTH, //
				bytes, 0, bytes.length);
		return bytes;
	}

	@Override
	public byte[] encrypt(byte[] data) {
		try {
			cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
			byte[] encrypted = cipher.doFinal(data);
			return mergeIvAndEncryptedData(encrypted, cipher.getIV());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public byte[] decrypt(byte[] encryptedBytesWithIv) {
		try {
			byte[] iv = getIv(encryptedBytesWithIv);
			byte[] bytes = getBytes(encryptedBytesWithIv);
			IvParameterSpec ivspec = new IvParameterSpec(iv);
			cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, ivspec);
			return cipher.doFinal(bytes);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public javax.crypto.Cipher getDecryptCipher(byte[] encryptedBytesWithIv) throws InvalidAlgorithmParameterException, InvalidKeyException {
		byte[] iv = getIv(encryptedBytesWithIv);
		IvParameterSpec ivspec = new IvParameterSpec(iv);
		cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, ivspec);
		return cipher;
	}

	@Override
	public javax.crypto.Cipher getEncryptCipher() throws InvalidKeyException {
		cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
		return cipher;
	}

	private byte[] getIv(byte[] encryptedBytesWithIv) {
		byte[] iv = new byte[IV_LENGTH];
		arraycopy( //
				encryptedBytesWithIv, 0, //
				iv, 0, IV_LENGTH);
		return iv;
	}

}
