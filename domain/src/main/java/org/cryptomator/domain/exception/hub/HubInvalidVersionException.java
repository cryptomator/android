package org.cryptomator.domain.exception.hub;

import org.cryptomator.domain.exception.BackendException;

public class HubInvalidVersionException extends BackendException {

	public HubInvalidVersionException(String message) {
		super(message);
	}

}
