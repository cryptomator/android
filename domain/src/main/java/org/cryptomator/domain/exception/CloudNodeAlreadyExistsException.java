package org.cryptomator.domain.exception;

public class CloudNodeAlreadyExistsException extends BackendException {

	public CloudNodeAlreadyExistsException(String name) {
		super(name);
	}
}
