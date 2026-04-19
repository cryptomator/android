package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.ui.activity.LicenseCheckActivity;

@Intent(LicenseCheckActivity.class)
public interface LicenseCheckIntent {

	/**
	 * Specifies an optional action identifier associated with the locked state.
	 *
	 * @return the action identifier, or {@code null} if absent
	 */
	@Optional
	String lockedAction();

}
