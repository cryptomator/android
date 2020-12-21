package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.util.Optional;

import java.io.Serializable;

public class VaultOrUnlockToken implements Serializable {

	public static VaultOrUnlockToken from(Vault vault) {
		return new VaultOrUnlockToken(vault, null);
	}

	public static VaultOrUnlockToken from(UnlockToken unlockToken) {
		return new VaultOrUnlockToken(null, unlockToken);
	}

	private final Vault vault;
	private final UnlockToken unlockToken;

	private VaultOrUnlockToken(Vault vault, UnlockToken unlockToken) {
		this.vault = vault;
		this.unlockToken = unlockToken;
	}

	public Optional<Vault> getVault() {
		return Optional.ofNullable(vault);
	}

	public Optional<UnlockToken> getUnlockToken() {
		return Optional.ofNullable(unlockToken);
	}

}
