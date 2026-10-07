package org.cryptomator.util.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.SecretKey;

abstract class BaseCipher implements Cipher {

	private final javax.crypto.Cipher cipher;
	private final SecretKey key;
	private final int ivLength;

	BaseCipher(javax.crypto.Cipher cipher, SecretKey key, int ivLength) {
		this.cipher = cipher;
		this.key = key;
		this.ivLength = ivLength;
	}

	protected abstract AlgorithmParameterSpec getIvParameterSpec(byte[] iv);

	@Override
	public byte[] encrypt(byte[] data) {
		try {
			cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
			byte[] encrypted = cipher.doFinal(data);
			return CryptoByteArrayUtils.join(encrypted, cipher.getIV());
		} catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
			throw new FatalCryptoException(e);
		}
	}

	@Override
	public byte[] decrypt(byte[] encryptedBytesWithIv) {
		try {
			byte[] iv = getIv(encryptedBytesWithIv);
			byte[] bytes = CryptoByteArrayUtils.getBytes(encryptedBytesWithIv, ivLength);
			AlgorithmParameterSpec ivspec = getIvParameterSpec(iv);
			cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, ivspec);
			return cipher.doFinal(bytes);
		} catch (InvalidKeyException | BadPaddingException | IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
			throw new FatalCryptoException(e);
		}
	}

	@Override
	public javax.crypto.Cipher getDecryptCipher(byte[] encryptedBytesWithIv) throws InvalidAlgorithmParameterException, InvalidKeyException {
		byte[] iv = getIv(encryptedBytesWithIv);
		AlgorithmParameterSpec ivspec = getIvParameterSpec(iv);
		cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, ivspec);
		return cipher;
	}

	@Override
	public javax.crypto.Cipher getEncryptCipher() throws InvalidKeyException {
		cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key);
		return cipher;
	}

	private byte[] getIv(byte[] encryptedBytesWithIv) {
		byte[] iv = new byte[ivLength];
		System.arraycopy(encryptedBytesWithIv, 0, iv, 0, iv.length);
		return iv;
	}
}
