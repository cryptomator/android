package org.cryptomator.domain.exception.update;

import org.cryptomator.domain.exception.BackendException;

public class GeneralUpdateErrorException extends BackendException {

	public GeneralUpdateErrorException(final String message) {
		super(message);
	}

	public GeneralUpdateErrorException(final String message, final Exception e) {
		super(message, e);
	}
}
