package org.cryptomator.data.cloud;

import static java.util.Arrays.asList;

import org.cryptomator.data.cloud.crypto.CryptoCloudContentRepositoryFactory;
import org.cryptomator.data.cloud.local.LocalStorageContentRepositoryFactory;
import org.cryptomator.data.cloud.s3.S3CloudContentRepositoryFactory;
import org.cryptomator.data.cloud.webdav.WebDavCloudContentRepositoryFactory;
import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CloudContentRepositoryFactories implements Iterable<CloudContentRepositoryFactory> {

	private final Iterable<CloudContentRepositoryFactory> factories;

	@Inject
	public CloudContentRepositoryFactories(
			S3CloudContentRepositoryFactory s3Factory, //
			CryptoCloudContentRepositoryFactory cryptoFactory, //
			LocalStorageContentRepositoryFactory localStorageFactory, //
			WebDavCloudContentRepositoryFactory webDavFactory) {

		factories = asList(s3Factory, //
				cryptoFactory, //
				localStorageFactory, //
				webDavFactory);
	}

	@NotNull
	@Override
	public Iterator<CloudContentRepositoryFactory> iterator() {
		return factories.iterator();
	}
}
