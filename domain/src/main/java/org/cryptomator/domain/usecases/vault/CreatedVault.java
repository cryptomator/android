package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;

public class CreatedVault {

	private final Vault vault;
	private final String recoveryKey;

	public CreatedVault(Vault vault, String recoveryKey) {
		this.vault = vault;
		this.recoveryKey = recoveryKey;
	}

	public Vault getVault() {
		return vault;
	}

	public String getRecoveryKey() {
		return recoveryKey;
	}
}
