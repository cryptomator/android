package org.cryptomator.domain.exception;

public class NoSuchBucketException extends BackendException {

	public NoSuchBucketException() {
	}

	public NoSuchBucketException(String name) {
		super(name);
	}
}
