package org.cryptomator.domain.exception.vaultconfig;

import com.auth0.jwt.exceptions.JWTVerificationException;

import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.exception.BackendException;

public class UnsupportedMasterkeyLocationException extends BackendException {

	UnverifiedVaultConfig unverifiedVaultConfig;

	public UnsupportedMasterkeyLocationException(UnverifiedVaultConfig unverifiedVaultConfig) {
		this.unverifiedVaultConfig = unverifiedVaultConfig;
	}

	public UnsupportedMasterkeyLocationException(String message, JWTVerificationException e) {
		super(message, e);
	}
}
