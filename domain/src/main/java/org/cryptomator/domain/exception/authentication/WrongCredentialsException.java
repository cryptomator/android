package org.cryptomator.domain.exception.authentication;

import org.cryptomator.domain.Cloud;

public class WrongCredentialsException extends AuthenticationException {

	public WrongCredentialsException(Cloud cloud) {
		super(cloud);
	}

}
