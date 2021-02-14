package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.WebDavCloudModel;
import org.cryptomator.presentation.ui.activity.WebDavAddOrChangeActivity;

@Intent(WebDavAddOrChangeActivity.class)
public interface WebDavAddOrChangeIntent {

	@Optional
	WebDavCloudModel webDavCloud();

	@Optional
	String preFilledURL();

}
