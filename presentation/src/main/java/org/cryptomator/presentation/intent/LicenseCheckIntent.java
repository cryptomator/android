package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.ui.activity.LicenseCheckActivity;

@Intent(LicenseCheckActivity.class)
public interface LicenseCheckIntent {

	@Optional
	String lockedAction();

}
