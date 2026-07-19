package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.SmbCloudModel;
import org.cryptomator.presentation.ui.activity.SmbAddOrChangeActivity;

@Intent(SmbAddOrChangeActivity.class)
public interface SmbAddOrChangeIntent {

	@Optional
	SmbCloudModel smbCloud();

}
