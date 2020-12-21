package org.cryptomator.domain.exception;

import org.cryptomator.domain.Vault;

public class NoSuchVaultException extends BackendException {

	private final Vault vault;

	public NoSuchVaultException(Vault vault, Throwable cause) {
		super(cause);
		this.vault = vault;
	}

	public Vault getVault() {
		return vault;
	}
}
