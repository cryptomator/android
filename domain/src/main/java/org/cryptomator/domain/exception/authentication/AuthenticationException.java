package org.cryptomator.domain.exception.authentication;

import android.content.Intent;

import org.cryptomator.domain.Cloud;
import org.cryptomator.util.Optional;

public abstract class AuthenticationException extends RuntimeException {

	private final Cloud cloud;

	AuthenticationException(Cloud cloud) {
		super("Authentication failed");
		this.cloud = cloud;
	}

	AuthenticationException(Cloud cloud, String message) {
		super(message);
		this.cloud = cloud;
	}

	public Cloud getCloud() {
		return cloud;
	}

	public Optional<Intent> getRecoveryAction() {
		return Optional.empty();
	}

}
