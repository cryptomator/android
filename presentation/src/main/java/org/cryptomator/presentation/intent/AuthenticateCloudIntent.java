package org.cryptomator.presentation.intent;

import org.cryptomator.domain.exception.authentication.AuthenticationException;
import org.cryptomator.generator.Intent;
import org.cryptomator.generator.Optional;
import org.cryptomator.presentation.model.CloudModel;
import org.cryptomator.presentation.ui.activity.AuthenticateCloudActivity;

@Intent(AuthenticateCloudActivity.class)
public interface AuthenticateCloudIntent {

	CloudModel cloud();

	@Optional
	AuthenticationException error();

	@Optional
	android.content.Intent recoveryAction();

}
