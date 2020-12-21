package org.cryptomator.domain.exception.update;

import org.cryptomator.domain.exception.BackendException;

public class SSLHandshakePreAndroid5UpdateCheckException extends BackendException {

	public SSLHandshakePreAndroid5UpdateCheckException(final String message, javax.net.ssl.SSLHandshakeException e) {
		super(message, e);
	}

}
