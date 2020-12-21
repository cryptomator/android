package org.cryptomator.domain.exception;

public class FatalBackendException extends RuntimeException {

	public FatalBackendException(Throwable e) {
		super(e);
	}

	public FatalBackendException(String message, Throwable e) {
		super(message, e);
	}

	public FatalBackendException(String message) {
		super(message);
	}

}
