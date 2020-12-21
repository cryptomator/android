package org.cryptomator.domain.exception.license;

import org.cryptomator.domain.exception.BackendException;

public class LicenseNotValidException extends BackendException {

	private final String license;

	public LicenseNotValidException(final String license) {
		this.license = license;
	}

	public String getLicense() {
		return license;
	}
}
