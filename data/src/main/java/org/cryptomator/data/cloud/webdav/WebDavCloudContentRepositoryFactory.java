package org.cryptomator.data.cloud.webdav;

import org.cryptomator.data.cloud.webdav.network.ConnectionHandlerFactory;
import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.WebDavCloud;
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;

import static org.cryptomator.domain.CloudType.WEBDAV;

public class WebDavCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final ConnectionHandlerFactory connectionHandlerFactory;

	@Inject
	WebDavCloudContentRepositoryFactory(ConnectionHandlerFactory connectionHandlerFactory) {
		this.connectionHandlerFactory = connectionHandlerFactory;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == WEBDAV;
	}

	@Override
	public CloudContentRepository cloudContentRepositoryFor(Cloud cloud) {
		WebDavCloud webDavCloud = (WebDavCloud) cloud;
		if (webDavCloud.username() == null || webDavCloud.password() == null) {
			throw new NoAuthenticationProvidedException(webDavCloud);
		}
		return new WebDavCloudContentRepository(webDavCloud, connectionHandlerFactory.createConnectionHandler(webDavCloud));
	}
}
