package org.cryptomator.domain.exception.hub;

import org.cryptomator.domain.exception.BackendException;

public class HubInvalidSetupCodeException extends BackendException {

	public HubInvalidSetupCodeException(Throwable e) {
		super(e);
	}

}
