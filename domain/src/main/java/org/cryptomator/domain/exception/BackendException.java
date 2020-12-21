package org.cryptomator.domain.exception;

public abstract class BackendException extends Exception {

	public BackendException() {
		super();
	}

	public BackendException(Throwable e) {
		super(e);
	}

	public BackendException(String message) {
		super(message);
	}

	public BackendException(String message, Throwable e) {
		super(message, e);
	}

}
