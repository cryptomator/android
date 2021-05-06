package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.presentation.model.VaultModel;
import org.cryptomator.presentation.ui.activity.UnlockVaultActivity;

@Intent(UnlockVaultActivity.class)
public interface UnlockVaultIntent {

	VaultModel vaultModel();

	VaultAction vaultAction();

	enum VaultAction {
		UNLOCK,
		UNLOCK_FOR_BIOMETRIC_AUTH,
		ENCRYPT_PASSWORD,
		CHANGE_PASSWORD
	}

}
