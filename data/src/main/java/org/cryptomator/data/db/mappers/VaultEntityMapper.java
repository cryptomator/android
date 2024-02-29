package org.cryptomator.data.db.mappers;

import org.cryptomator.data.db.CloudDao;
import org.cryptomator.data.db.entities.VaultEntity;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static org.cryptomator.domain.Vault.aVault;

@Singleton
public class VaultEntityMapper extends EntityMapper<VaultEntity, Vault> {

	private final CloudEntityMapper cloudEntityMapper;
	private final Provider<CloudDao> cloudDao;

	@Inject
	public VaultEntityMapper(CloudEntityMapper cloudEntityMapper, Provider<CloudDao> cloudDao) {
		this.cloudDao = cloudDao;
		this.cloudEntityMapper = cloudEntityMapper;
	}

	@Override
	public Vault fromEntity(VaultEntity entity) throws BackendException {
		return aVault() //
				.withId(entity.getId()) //
				.withName(entity.getFolderName()) //
				.withPath(entity.getFolderPath()) //
				.withCloud(cloudFrom(entity)) //
				.withCloudType(CloudType.valueOf(entity.getCloudType())) //
				.withSavedPassword(entity.getPassword()) //
				.withPosition(entity.getPosition()) //
				.withFormat(entity.getFormat()) //
				.withShorteningThreshold(entity.getShorteningThreshold()) //
				.build();
	}

	private Cloud cloudFrom(VaultEntity entity) {
		if (entity.getFolderCloudId() == null) {
			return null;
		}
		return cloudEntityMapper.fromEntity(cloudDao.get().load(entity.getFolderCloudId()));
	}

	@Override
	public VaultEntity toEntity(Vault domainObject) {
		Long folderCloudId = null;
		if (domainObject.getCloud() != null) {
			folderCloudId = cloudEntityMapper.toEntity(domainObject.getCloud()).getId();
		}
		return new VaultEntity(domainObject.getId(), //
				folderCloudId, //
				domainObject.getPath(), //
				domainObject.getName(), //
				domainObject.getCloudType().name(), //
				domainObject.getPassword(), //
				domainObject.getPosition(), //
				domainObject.getFormat(), //
				domainObject.getShorteningThreshold() //
		);
	}
}
