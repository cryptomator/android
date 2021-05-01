package org.cryptomator.domain.exception.update;

public class HashMismatchUpdateCheckException extends GeneralUpdateErrorException {

	public HashMismatchUpdateCheckException(final String message) {
		super(message);
	}

}
