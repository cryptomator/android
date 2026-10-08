package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.SharepointCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.SharepointRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class GetSharepointDrives {

	private final SharepointRepository sharepointRepository;
	private final SharepointCloud cloud;

	public GetSharepointDrives(SharepointRepository sharepointRepository, @Parameter SharepointCloud cloud) {
		this.sharepointRepository = sharepointRepository;
		this.cloud = cloud;
	}

	public List<SharepointCloud> execute() throws BackendException {
		return sharepointRepository.drives(cloud);
	}
}
