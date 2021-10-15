package org.cryptomator.domain.exception.authentication;

import android.content.Intent;

import com.google.common.base.Optional;

import org.cryptomator.domain.Cloud;

public class UserRecoverableAuthenticationException extends AuthenticationException {

	private final transient Intent recoveryAction;

	public UserRecoverableAuthenticationException(Cloud cloud, Intent recoveryAction) {
		super(cloud);
		this.recoveryAction = recoveryAction;
	}

	public Optional<Intent> getRecoveryAction() {
		return Optional.fromNullable(recoveryAction);
	}

}
