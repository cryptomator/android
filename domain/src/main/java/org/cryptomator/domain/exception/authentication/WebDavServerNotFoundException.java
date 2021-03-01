package org.cryptomator.domain.exception.authentication;

import org.cryptomator.domain.Cloud;

public class WebDavServerNotFoundException extends AuthenticationException {

	public WebDavServerNotFoundException(Cloud cloud) {
		super(cloud);
	}
}
