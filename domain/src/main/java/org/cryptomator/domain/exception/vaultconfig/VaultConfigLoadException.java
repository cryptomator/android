package org.cryptomator.domain.exception.vaultconfig;

import org.cryptomator.domain.exception.BackendException;

public class VaultConfigLoadException extends BackendException {

	public VaultConfigLoadException(String message) {
		super(message);
	}

	public VaultConfigLoadException(String message, Throwable e) {
		super(message, e);
	}

	public VaultConfigLoadException(Exception e) {
		super(e);
	}

}
