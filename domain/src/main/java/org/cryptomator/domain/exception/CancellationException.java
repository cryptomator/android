package org.cryptomator.domain.exception;

/**
 * Thrown if an operation has willingly be canceld.
 */
public class CancellationException extends FatalBackendException {

	public CancellationException() {
		super("Operation canceled");
	}

	public CancellationException(Throwable cause) {
		super("Operation canceled", cause);
	}

}
