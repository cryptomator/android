package org.cryptomator.domain.exception.authentication;

import org.cryptomator.domain.Cloud;

public class WebDavNotSupportedException extends AuthenticationException {

	public WebDavNotSupportedException(Cloud cloud) {
		super(cloud, "WebDav not supported by server");
	}
}
