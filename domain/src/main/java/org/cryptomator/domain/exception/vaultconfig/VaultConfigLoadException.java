package org.cryptomator.domain.exception.vaultconfig;

import com.auth0.jwt.exceptions.JWTVerificationException;

import org.cryptomator.domain.exception.BackendException;

public class VaultConfigLoadException extends BackendException {

	public VaultConfigLoadException(String message) {
		super(message);
	}

	public VaultConfigLoadException(String message, JWTVerificationException e) {
		super(message, e);
	}

	public VaultConfigLoadException(Exception e) {
		super(e);
	}

}
