package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.SmbCloudModel;
import org.cryptomator.presentation.ui.activity.SmbAddOrChangeActivity;

/**
 * Intent interface for navigating to the SMB connection setup or edit screen.
 * Uses the generator to create an 'IntentBuilder'.
 */
@Intent(SmbAddOrChangeActivity.class)
public interface SmbAddOrChangeIntent {

	/**
	 * Optional parameter to pass an existing SMB connection model for editing.
	 */
	@Optional
	SmbCloudModel smbCloud();

}
