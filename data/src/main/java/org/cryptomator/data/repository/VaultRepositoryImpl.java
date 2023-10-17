package org.cryptomator.data.repository;

import static org.cryptomator.domain.Vault.aCopyOf;

import android.database.sqlite.SQLiteConstraintException;

import org.cryptomator.data.cloud.crypto.CryptoCloudContentRepositoryFactory;
import org.cryptomator.data.cloud.crypto.CryptoCloudFactory;
import org.cryptomator.data.db.VaultDao;
import org.cryptomator.data.db.mappers.VaultEntityMapper;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.VaultAlreadyExistException;
import org.cryptomator.domain.repository.VaultRepository;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class VaultRepositoryImpl implements VaultRepository {

	private final VaultDao vaultDao;
	private final VaultEntityMapper mapper;
	private final CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory;
	private final DispatchingCloudContentRepository dispatchingCloudContentRepository;
	private final CryptoCloudFactory cryptoCloudFactory;

	@Inject
	public VaultRepositoryImpl( //
			VaultEntityMapper mapper, //
			CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory, //
			CryptoCloudFactory cryptoCloudFactory, //
			DispatchingCloudContentRepository dispatchingCloudContentRepository, //
			VaultDao vaultDao) {
		this.mapper = mapper;
		this.vaultDao = vaultDao;
		this.cryptoCloudContentRepositoryFactory = cryptoCloudContentRepositoryFactory;
		this.cryptoCloudFactory = cryptoCloudFactory;
		this.dispatchingCloudContentRepository = dispatchingCloudContentRepository;
	}

	@Override
	public List<Vault> vaults() throws BackendException {
		List<Vault> result = new ArrayList<>();
		for (Vault vault : mapper.fromEntities(vaultDao.loadAll())) {
			result.add(aCopyOf(vault).withUnlocked(isUnlocked(vault)).build());
		}
		return result;
	}

	@Override
	public Vault store(Vault vault) throws BackendException {
		try {
			return mapper.fromEntity(vaultDao.storeReplacingAndReload(mapper.toEntity(vault)));
		} catch (SQLiteConstraintException e) {
			throw new VaultAlreadyExistException();
		}
	}

	@Override
	public Long delete(Vault vault) throws BackendException {
		deregisterUnlocked(vault);
		dispatchingCloudContentRepository.removeCloudContentRepositoryFor(cryptoCloudFactory.decryptedViewOf(vault));
		vaultDao.delete(mapper.toEntity(vault));
		return vault.getId();
	}

	@Override
	public Vault load(Long id) throws BackendException {
		Vault vault = mapper.fromEntity(vaultDao.load(id));
		return aCopyOf(vault).withUnlocked(isUnlocked(vault)).build();
	}

	private void deregisterUnlocked(Vault vault) {
		if (isUnlocked(vault)) {
			cryptoCloudContentRepositoryFactory.deregisterCryptor(vault);
		}
	}

	private boolean isUnlocked(Vault vault) {
		return cryptoCloudContentRepositoryFactory.cryptorIsRegisteredFor(vault);
	}

	@Override
	public void assertUnlocked(Vault vault) {
		cryptoCloudContentRepositoryFactory.assertCryptorRegisteredFor(vault);
	}

}
