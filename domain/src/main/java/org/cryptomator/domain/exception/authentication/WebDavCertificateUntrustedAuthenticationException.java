package org.cryptomator.domain.exception.authentication;

import org.cryptomator.domain.Cloud;

public class WebDavCertificateUntrustedAuthenticationException extends AuthenticationException {

	private final String certificate;

	public WebDavCertificateUntrustedAuthenticationException(Cloud cloud, String certificate) {
		super(cloud);
		this.certificate = certificate;
	}

	public String getCertificate() {
		return certificate;
	}
}
