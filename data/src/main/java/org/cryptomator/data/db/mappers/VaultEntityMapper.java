package org.cryptomator.data.db.mappers;

import org.cryptomator.data.db.entities.VaultEntity;
import org.cryptomator.data.db.entities.VaultWithCloud;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.util.crypto.CryptoMode;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.Vault.aVault;

@Singleton
public class VaultEntityMapper extends EntityMapper<VaultWithCloud, Vault> {

	private final CloudEntityMapper cloudEntityMapper;

	@Inject
	public VaultEntityMapper(CloudEntityMapper cloudEntityMapper) {
		this.cloudEntityMapper = cloudEntityMapper;
	}

	@Override
	public Vault fromEntity(VaultWithCloud entity) throws BackendException {
		VaultEntity vault = entity.getVault();
		return aVault() //
				.withId(vault.getId()) //
				.withName(vault.getFolderName()) //
				.withPath(vault.getFolderPath()) //
				.withCloud(cloudFrom(entity)) //
				.withCloudType(CloudType.valueOf(vault.getCloudType())) //
				.withSavedPassword(vault.getPassword(), cryptoModeFrom(vault)) //
				.withPosition(vault.getPosition()) //
				.withFormat(vault.getFormat()) //
				.withShorteningThreshold(vault.getShorteningThreshold()) //
				.build();
	}

	private Cloud cloudFrom(VaultWithCloud entity) {
		if (entity.getFolderCloud() == null) {
			return null;
		}
		return cloudEntityMapper.fromEntity(entity.getFolderCloud());
	}

	private CryptoMode cryptoModeFrom(VaultEntity entity) {
		return entity.getPasswordCryptoMode() != null ? CryptoMode.valueOf(entity.getPasswordCryptoMode()) : null;
	}

	@Override
	public VaultWithCloud toEntity(Vault domainObject) {
		VaultEntity vault = new VaultEntity();
		vault.setId(domainObject.getId());
		vault.setFolderPath(domainObject.getPath());
		vault.setFolderName(domainObject.getName());
		vault.setCloudType(domainObject.getCloudType().name());
		vault.setPassword(domainObject.getPassword());
		if (domainObject.getPasswordCryptoMode() != null) {
			vault.setPasswordCryptoMode(domainObject.getPasswordCryptoMode().name());
		}
		vault.setPosition(domainObject.getPosition());
		vault.setFormat(domainObject.getFormat());
		vault.setShorteningThreshold(domainObject.getShorteningThreshold());

		VaultWithCloud result = new VaultWithCloud();
		result.setVault(vault);
		if (domainObject.getCloud() != null) {
			result.setFolderCloud(cloudEntityMapper.toEntity(domainObject.getCloud()));
			vault.setFolderCloudId(result.getFolderCloud().getId());
		}
		return result;
	}
}
