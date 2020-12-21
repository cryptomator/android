package org.cryptomator.domain.exception;

public class NoSuchCloudFileException extends BackendException {

	public NoSuchCloudFileException() {
	}

	public NoSuchCloudFileException(String name) {
		super(name);
	}
}
