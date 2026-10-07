package org.cryptomator.domain.exception.hub;

import org.cryptomator.domain.exception.BackendException;

public class HubAuthenticationFailedException extends BackendException {

	public HubAuthenticationFailedException() {
		super();
	}

	public HubAuthenticationFailedException(Exception e) {
		super(e);
	}

}
