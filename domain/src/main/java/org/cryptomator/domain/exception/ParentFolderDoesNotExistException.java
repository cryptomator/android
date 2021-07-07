package org.cryptomator.domain.exception;

public class ParentFolderDoesNotExistException extends BackendException {

	public ParentFolderDoesNotExistException() {
		super();
	}

	public ParentFolderDoesNotExistException(String name) {
		super(name);
	}

}
