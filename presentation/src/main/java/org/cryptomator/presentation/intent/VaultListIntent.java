package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.ui.activity.VaultListActivity;

@Intent(VaultListActivity.class)
public interface VaultListIntent {

	@Optional
	Boolean stopEditFileNotification();

}
