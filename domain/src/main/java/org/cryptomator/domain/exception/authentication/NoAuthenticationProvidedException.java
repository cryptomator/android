package org.cryptomator.domain.exception.authentication;

import org.cryptomator.domain.Cloud;

public class NoAuthenticationProvidedException extends AuthenticationException {

	public NoAuthenticationProvidedException(Cloud cloud) {
		super(cloud);
	}

}
