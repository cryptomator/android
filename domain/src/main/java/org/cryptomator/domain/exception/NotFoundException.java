package org.cryptomator.domain.exception;

public class NotFoundException extends BackendException {

	public NotFoundException() {
	}

	public NotFoundException(String name) {
		super(name);
	}
}
