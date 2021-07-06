package org.cryptomator.util.crypto;

import java.security.GeneralSecurityException;

public class FatalCryptoException extends RuntimeException {

	public FatalCryptoException(GeneralSecurityException e) {
		super(e);
	}
}
