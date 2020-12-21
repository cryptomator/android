package org.cryptomator.domain.exception;

import java.security.cert.CertificateException;

public class NotTrustableCertificateException extends CertificateException {

	public NotTrustableCertificateException(String message) {
		super(message);
	}

	public NotTrustableCertificateException(String message, Throwable cause) {
		super(message, cause);
	}
}
