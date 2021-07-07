package org.cryptomator.data.cloud.onedrive;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.OnedriveCloud;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.CloudType.ONEDRIVE;

@Singleton
public class OnedriveCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;

	@Inject
	public OnedriveCloudContentRepositoryFactory(Context context) {
		this.context = context;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == ONEDRIVE;
	}

	@Override
	public CloudContentRepository<OnedriveCloud, OnedriveNode, OnedriveFolder, OnedriveFile> cloudContentRepositoryFor(Cloud cloud) {
		return new OnedriveCloudContentRepository((OnedriveCloud) cloud, context);
	}
}
