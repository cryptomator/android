package org.cryptomator.data.repository;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptomator.data.db.mappers.DeploymentInfoMapper;
import org.cryptomator.data.db.mappers.VaultRemoteMapper;
import org.cryptomator.data.util.Mock;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.model.DeploymentInfo;
import org.cryptomator.domain.repository.DeploymentRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@Singleton
public class DeploymentRepositoryImpl implements DeploymentRepository {

	@Inject
	public DeploymentRepositoryImpl() {}

	@Override
	public @NotNull List<DeploymentInfo> getDeploymentInfo() {
		var dto = Mock.Companion.getDeploymentInfo();
		VaultRemoteMapper vaultRemoteMapper = new VaultRemoteMapper();
		DeploymentInfoMapper mapper = new DeploymentInfoMapper(vaultRemoteMapper);
		DeploymentInfo deploymentInfo = mapper.toDomain(dto);
		return Collections.singletonList(deploymentInfo);
	}
}
