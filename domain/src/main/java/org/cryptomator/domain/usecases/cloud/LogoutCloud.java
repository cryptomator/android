package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.DropboxCloud;
import org.cryptomator.domain.GoogleDriveCloud;
import org.cryptomator.domain.OnedriveCloud;
import org.cryptomator.domain.PCloudCloud;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class LogoutCloud {

	private final CloudContentRepository<Cloud, ?, ?, ?> cloudContentRepository;
	private final CloudRepository cloudRepository;
	private final Cloud cloud;

	public LogoutCloud(CloudContentRepository cloudContentRepository, CloudRepository cloudRepository, @Parameter Cloud cloud) {
		this.cloudContentRepository = cloudContentRepository;
		this.cloudRepository = cloudRepository;
		this.cloud = cloud;
	}

	public Cloud execute() throws BackendException {
		cloudContentRepository.logout(cloud);
		return cloudRepository.store(cloudWithUsernameAndAccessTokenRemoved(cloud));
	}

	private Cloud cloudWithUsernameAndAccessTokenRemoved(Cloud cloud) {
		if (cloud instanceof DropboxCloud) {
			return DropboxCloud //
					.aCopyOf((DropboxCloud) cloud) //
					.withUsername(null) //
					.withAccessToken(null) //
					.build();
		} else if (cloud instanceof GoogleDriveCloud) {
			return GoogleDriveCloud //
					.aCopyOf((GoogleDriveCloud) cloud) //
					.withUsername(null) //
					.withAccessToken(null) //
					.build();
		} else if (cloud instanceof OnedriveCloud) {
			return OnedriveCloud //
					.aCopyOf((OnedriveCloud) cloud) //
					.withUsername(null) //
					.withAccessToken(null) //
					.build();
		} else if (cloud instanceof PCloudCloud) {
			return PCloudCloud //
					.aCopyOf((PCloudCloud) cloud) //
					.withUsername(null) //
					.withAccessToken(null) //
					.build();
		}
		throw new IllegalStateException("Logout not supported for cloud with type " + cloud.type());
	}
}
