package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.model.DeploymentInfo;
import org.cryptomator.domain.repository.DeploymentRepository;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class GetDeploymentInfo {

	private final DeploymentRepository repository;



	public GetDeploymentInfo(DeploymentRepository repository) {
		this.repository = repository;
	}

	public List<DeploymentInfo> execute() throws BackendException {
		return repository.getDeploymentInfo();
	}
}
