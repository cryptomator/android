package org.cryptomator.data.repository;

import com.google.common.base.Optional;

import org.cryptomator.data.cloud.crypto.CryptoCloudFactory;
import org.cryptomator.data.db.Database;
import org.cryptomator.data.db.entities.CloudEntity;
import org.cryptomator.data.db.mappers.CloudEntityMapper;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.domain.usecases.vault.UnlockToken;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class CloudRepositoryImpl implements CloudRepository {

	private final Database database;
	private final CryptoCloudFactory cryptoCloudFactory;
	private final CloudEntityMapper mapper;
	private final DispatchingCloudContentRepository dispatchingCloudContentRepository;

	@Inject
	public CloudRepositoryImpl(CloudEntityMapper mapper, //
			CryptoCloudFactory cryptoCloudFactory, //
			Database database, //
			DispatchingCloudContentRepository dispatchingCloudContentRepository) {
		this.database = database;
		this.cryptoCloudFactory = cryptoCloudFactory;
		this.mapper = mapper;
		this.dispatchingCloudContentRepository = dispatchingCloudContentRepository;
	}

	@Override
	public List<Cloud> clouds(CloudType cloudType) throws BackendException {
		List<Cloud> cloudsFromType = new ArrayList<>();
		List<Cloud> allClouds = mapper.fromEntities(database.loadAll(CloudEntity.class));

		for (Cloud cloud : allClouds) {
			if (cloud.type().equals(cloudType)) {
				cloudsFromType.add(cloud);
			}
		}

		return cloudsFromType;
	}

	@Override
	public List<Cloud> allClouds() throws BackendException {
		return mapper.fromEntities(database.loadAll(CloudEntity.class));
	}

	@Override
	public Cloud store(Cloud cloud) {
		if (!cloud.persistent()) {
			throw new IllegalArgumentException("Can not store non persistent cloud");
		}

		Cloud storedCloud = mapper.fromEntity(database.store(mapper.toEntity(cloud)));

		dispatchingCloudContentRepository.removeCloudContentRepositoryFor(storedCloud);
		database.clearCache();

		return storedCloud;
	}

	@Override
	public void delete(Cloud cloud) {
		if (!cloud.persistent()) {
			throw new IllegalArgumentException("Can not delete non persistent cloud");
		}
		database.delete(mapper.toEntity(cloud));
	}

	@Override
	public void create(CloudFolder location, CharSequence password) throws BackendException {
		cryptoCloudFactory.create(location, password);
	}

	@Override
	public Cloud decryptedViewOf(Vault vault) throws BackendException {
		return cryptoCloudFactory.decryptedViewOf(vault);
	}

	public Optional<UnverifiedVaultConfig> unverifiedVaultConfig(Vault vault) throws BackendException {
		return cryptoCloudFactory.unverifiedVaultConfig(vault);
	}

	@Override
	public Cloud unlock(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password, Flag cancelledFlag) throws BackendException {
		Vault vaultWithVersion = cryptoCloudFactory.unlock(vault, unverifiedVaultConfig, password, cancelledFlag);
		return decryptedViewOf(vaultWithVersion);
	}

	@Override
	public Cloud unlock(UnlockToken token, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password, Flag cancelledFlag) throws BackendException {
		Vault vaultWithVersion = cryptoCloudFactory.unlock(token, unverifiedVaultConfig, password, cancelledFlag);
		return decryptedViewOf(vaultWithVersion);
	}

	@Override
	public UnlockToken prepareUnlock(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig) throws BackendException {
		return cryptoCloudFactory.createUnlockToken(vault, unverifiedVaultConfig);
	}

	@Override
	public boolean isVaultPasswordValid(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password) throws BackendException {
		return cryptoCloudFactory.isVaultPasswordValid(vault, unverifiedVaultConfig, password);
	}

	@Override
	public void lock(Vault vault) throws BackendException {
		dispatchingCloudContentRepository.removeCloudContentRepositoryFor(decryptedViewOf(vault));
		cryptoCloudFactory.lock(vault);
	}

	@Override
	public void changePassword(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, String oldPassword, String newPassword) throws BackendException {
		cryptoCloudFactory.changePassword(vault, unverifiedVaultConfig, oldPassword, newPassword);
	}

}
