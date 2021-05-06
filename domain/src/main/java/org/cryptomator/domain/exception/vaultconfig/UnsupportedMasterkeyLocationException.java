package org.cryptomator.domain.exception.vaultconfig;

import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.exception.BackendException;

import io.jsonwebtoken.JwtException;

public class UnsupportedMasterkeyLocationException extends BackendException {

	UnverifiedVaultConfig unverifiedVaultConfig;

	public UnsupportedMasterkeyLocationException(UnverifiedVaultConfig unverifiedVaultConfig) {
		this.unverifiedVaultConfig = unverifiedVaultConfig;
	}

	public UnsupportedMasterkeyLocationException(String message, JwtException e) {
		super(message, e);
	}
}
