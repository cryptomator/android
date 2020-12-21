package org.cryptomator.data.repository;

import org.cryptomator.data.cloud.CloudContentRepositoryFactories;
import org.cryptomator.data.cloud.crypto.CryptoCloud;
import org.cryptomator.data.cloud.crypto.CryptoCloudContentRepositoryFactory;
import org.cryptomator.data.util.NetworkConnectionCheck;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.authentication.AuthenticationException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.cloud.DownloadState;
import org.cryptomator.domain.usecases.cloud.UploadState;
import org.cryptomator.util.Optional;

import java.io.File;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DispatchingCloudContentRepository implements CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> {

	private final Map<Cloud, CloudContentRepository> delegates = new WeakHashMap<>();
	private final CloudContentRepositoryFactories cloudContentRepositoryFactories;
	private final NetworkConnectionCheck networkConnectionCheck;
	private final CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory;

	@Inject
	public DispatchingCloudContentRepository(CloudContentRepositoryFactories cloudContentRepositoryFactories, NetworkConnectionCheck networkConnectionCheck,
			CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory) {
		this.cloudContentRepositoryFactories = cloudContentRepositoryFactories;
		this.networkConnectionCheck = networkConnectionCheck;
		this.cryptoCloudContentRepositoryFactory = cryptoCloudContentRepositoryFactory;
	}

	@Override
	public CloudFolder root(Cloud cloud) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(cloud);
			return delegateFor(cloud).root(cloud);
		} catch (AuthenticationException e) {
			delegates.remove(cloud);
			throw e;
		}
	}

	@Override
	public CloudFolder resolve(Cloud cloud, String path) throws BackendException {
		try {
			// do not check for network connection
			return delegateFor(cloud).resolve(cloud, path);
		} catch (AuthenticationException e) {
			delegates.remove(cloud);
			throw e;
		}
	}

	@Override
	public CloudFile file(CloudFolder parent, String name) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(parent.getCloud());
			return delegateFor(parent).file(parent, name);
		} catch (AuthenticationException e) {
			delegates.remove(parent.getCloud());
			throw e;
		}
	}

	@Override
	public CloudFile file(CloudFolder parent, String name, Optional<Long> size) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(parent.getCloud());
			return delegateFor(parent).file(parent, name, size);
		} catch (AuthenticationException e) {
			delegates.remove(parent.getCloud());
			throw e;
		}
	}

	@Override
	public CloudFolder folder(CloudFolder parent, String name) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(parent.getCloud());
			return delegateFor(parent).folder(parent, name);
		} catch (AuthenticationException e) {
			delegates.remove(parent.getCloud());
			throw e;
		}
	}

	@Override
	public boolean exists(CloudNode node) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(node.getCloud());
			return delegateFor(node).exists(node);
		} catch (AuthenticationException e) {
			delegates.remove(node.getCloud());
			throw e;
		}
	}

	@Override
	public List<CloudNode> list(CloudFolder folder) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(folder.getCloud());
			return delegateFor(folder).list(folder);
		} catch (AuthenticationException e) {
			delegates.remove(folder.getCloud());
			throw e;
		}
	}

	@Override
	public CloudFolder create(CloudFolder folder) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(folder.getCloud());
			return delegateFor(folder).create(folder);
		} catch (AuthenticationException e) {
			delegates.remove(folder.getCloud());
			throw e;
		}
	}

	@Override
	public CloudFolder move(CloudFolder source, CloudFolder target) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(source.getCloud());
			if (!source.getCloud().equals(target.getCloud())) {
				throw new IllegalArgumentException("Cloud of parameters must match");
			}
			return delegateFor(source).move(source, target);
		} catch (AuthenticationException e) {
			delegates.remove(source.getCloud());
			throw e;
		}
	}

	@Override
	public CloudFile move(CloudFile source, CloudFile target) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(source.getCloud());
			if (!source.getCloud().equals(target.getCloud())) {
				throw new IllegalArgumentException("Cloud of parameters must match");
			}
			return delegateFor(source).move(source, target);
		} catch (AuthenticationException e) {
			delegates.remove(source.getCloud());
			throw e;
		}
	}

	@Override
	public CloudFile write(CloudFile source, DataSource data, ProgressAware<UploadState> progressAware, boolean replace, long size) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(source.getCloud());
			return delegateFor(source).write(source, data, progressAware, replace, size);
		} catch (AuthenticationException e) {
			delegates.remove(source.getCloud());
			throw e;
		}
	}

	@Override
	public void read(CloudFile file, Optional<File> tempEncryptedFile, OutputStream data, ProgressAware<DownloadState> progressAware) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(file.getCloud());
			delegateFor(file).read(file, tempEncryptedFile, data, progressAware);
		} catch (AuthenticationException e) {
			delegates.remove(file.getCloud());
			throw e;
		}
	}

	@Override
	public void delete(CloudNode node) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(node.getCloud());
			delegateFor(node).delete(node);
		} catch (AuthenticationException e) {
			delegates.remove(node.getCloud());
			throw e;
		}
	}

	@Override
	public String checkAuthenticationAndRetrieveCurrentAccount(Cloud cloud) throws BackendException {
		try {
			networkConnectionCheck.assertConnectionIsPresent(cloud);
			return delegateFor(cloud).checkAuthenticationAndRetrieveCurrentAccount(cloud);
		} catch (AuthenticationException e) {
			delegates.remove(cloud);
			throw e;
		}
	}

	@Override
	public void logout(Cloud cloud) throws BackendException {
		delegateFor(cloud).logout(cloud);
		removeCloudContentRepositoryFor(cloud);
	}

	public void removeCloudContentRepositoryFor(Cloud cloud) {
		Iterator<Cloud> clouds = delegates.keySet().iterator();
		while (clouds.hasNext()) {
			Cloud current = clouds.next();
			if (cloud.equals(current)) {
				clouds.remove();
			} else if (cloudIsDelegateOfCryptoCloud(current, cloud)) {
				cryptoCloudContentRepositoryFactory.deregisterCryptor(((CryptoCloud) current).getVault(), false);
			}
		}
	}

	private boolean cloudIsDelegateOfCryptoCloud(Cloud potentialCryptoCloud, Cloud cloud) {
		if (potentialCryptoCloud instanceof CryptoCloud) {
			CryptoCloud cryptoCloud = (CryptoCloud) potentialCryptoCloud;
			Cloud delegate = cryptoCloud.getVault().getCloud();
			return cloud.equals(delegate);
		}
		return false;
	}

	private CloudContentRepository delegateFor(CloudNode cloudNode) {
		return delegateFor(cloudNode.getCloud());
	}

	private CloudContentRepository delegateFor(Cloud cloud) {
		if (!delegates.containsKey(cloud)) {
			delegates.put(cloud, createCloudContentRepositoryFor(cloud));
		}
		return delegates.get(cloud);
	}

	private CloudContentRepository createCloudContentRepositoryFor(Cloud cloud) {
		for (CloudContentRepositoryFactory cloudContentRepositoryFactory : cloudContentRepositoryFactories) {
			if (cloudContentRepositoryFactory.supports(cloud)) {
				return cloudContentRepositoryFactory.cloudContentRepositoryFor(cloud);
			}
		}
		throw new IllegalStateException("Unsupported cloud " + cloud);
	}
}
