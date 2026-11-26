package org.cryptomator.domain.exception;

public class UnableToDecryptSmbPasswordException extends FatalBackendException {

	public UnableToDecryptSmbPasswordException(RuntimeException exception) {
		super(exception);
	}
}
