package org.cryptomator.domain.exception.authentication;

import org.cryptomator.domain.Cloud;

/**
 * The cloud's server is in the local network, which the app may only reach after the user granted the
 * ACCESS_LOCAL_NETWORK permission.
 */
public class LocalNetworkPermissionMissingException extends AuthenticationException {

	public LocalNetworkPermissionMissingException(Cloud cloud) {
		super(cloud);
	}

}
