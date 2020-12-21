package org.cryptomator.domain.exception.authentication;

import android.content.Intent;

import org.cryptomator.domain.Cloud;
import org.cryptomator.util.Optional;

public class UserRecoverableAuthenticationException extends AuthenticationException {

	private final transient Intent recoveryAction;

	public UserRecoverableAuthenticationException(Cloud cloud, Intent recoveryAction) {
		super(cloud);
		this.recoveryAction = recoveryAction;
	}

	public Optional<Intent> getRecoveryAction() {
		return Optional.ofNullable(recoveryAction);
	}

}
