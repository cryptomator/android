package org.cryptomator.data.repository;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.exception.authentication.AuthenticationException;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.repository.CloudContentRepository;

public interface CloudContentRepositoryFactory {

	boolean supports(Cloud cloud);

	/**
	 * Creates a new {@link CloudContentRepository}.
	 * 
	 * @param cloud the {@link Cloud} to access through the {@code CloudContentRepository}
	 * @return the created {@code CloudContentRepository}
	 * 
	 * @throws NoAuthenticationProvidedException if the cloud has not been authenticated
	 * @throws AuthenticationException if an authentication error occurs while accessing the cloud
	 */
	CloudContentRepository cloudContentRepositoryFor(Cloud cloud);

}
