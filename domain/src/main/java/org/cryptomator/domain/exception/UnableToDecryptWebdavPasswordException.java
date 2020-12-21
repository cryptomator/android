package org.cryptomator.domain.exception;

public class UnableToDecryptWebdavPasswordException extends FatalBackendException {

	public UnableToDecryptWebdavPasswordException(RuntimeException exception) {
		super(exception);
	}
}
