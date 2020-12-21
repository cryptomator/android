package org.cryptomator.util.crypto;

import java.security.InvalidAlgorithmParameterException;

interface KeyGenerator {

	void createKey(boolean requireUserAuthentication) throws InvalidAlgorithmParameterException;

}
