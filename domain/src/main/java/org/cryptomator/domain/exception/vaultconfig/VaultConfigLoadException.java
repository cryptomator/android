package org.cryptomator.domain.exception.vaultconfig;

import org.cryptomator.domain.exception.BackendException;

import io.jsonwebtoken.JwtException;

public class VaultConfigLoadException extends BackendException {

	public VaultConfigLoadException(String message) {
		super(message);
	}

	public VaultConfigLoadException(String message, JwtException e) {
		super(message, e);
	}
}
