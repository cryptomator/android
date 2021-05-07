package org.cryptomator.presentation.intent;

import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.S3CloudModel;
import org.cryptomator.presentation.ui.activity.S3AddOrChangeActivity;

@Intent(S3AddOrChangeActivity.class)
public interface S3AddOrChangeIntent {

	@Optional
	S3CloudModel s3Cloud();
}
