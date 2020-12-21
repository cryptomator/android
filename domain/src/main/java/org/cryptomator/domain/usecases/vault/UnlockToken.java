package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;

import java.io.Serializable;

public interface UnlockToken extends Serializable {

	Vault getVault();

}
