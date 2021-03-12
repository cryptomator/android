package org.cryptomator.data.cloud.pcloud;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.PCloudCloud;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.CloudType.PCLOUD;

@Singleton
public class PCloudCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;

	@Inject
	public PCloudCloudContentRepositoryFactory(Context context) {
		this.context = context;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == PCLOUD;
	}

	@Override
	public CloudContentRepository cloudContentRepositoryFor(Cloud cloud) {
		return new PCloudCloudContentRepository((PCloudCloud) cloud, context);
	}

}
