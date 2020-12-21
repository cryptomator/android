package org.cryptomator.domain.repository;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.exception.BackendException;

public interface CloudAuthenticationService {

	boolean isAuthenticated(Cloud cloud) throws BackendException;

	boolean canAuthenticate(Cloud cloud);

	Cloud updateAuthenticatedCloud(Cloud cloud);
}
