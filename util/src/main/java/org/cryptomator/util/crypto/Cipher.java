package org.cryptomator.util.crypto;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;

interface Cipher {

	byte[] encrypt(byte[] data);

	byte[] decrypt(byte[] data);

	javax.crypto.Cipher getDecryptCipher(byte[] encryptedBytesWithIv) throws InvalidAlgorithmParameterException, InvalidKeyException;

	javax.crypto.Cipher getEncryptCipher() throws InvalidKeyException;

}
