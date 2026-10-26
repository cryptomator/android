package org.cryptomator.data.cloud.microsoftgraph;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.MicrosoftGraphCloud;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.CloudType.ONEDRIVE;
import static org.cryptomator.domain.CloudType.SHAREPOINT;

@Singleton
public class MicrosoftGraphCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;

	@Inject
	public MicrosoftGraphCloudContentRepositoryFactory(Context context) {
		this.context = context;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == ONEDRIVE || cloud.type() == SHAREPOINT;
	}

	@Override
	public CloudContentRepository<MicrosoftGraphCloud, MicrosoftGraphNode, MicrosoftGraphFolder, MicrosoftGraphFile> cloudContentRepositoryFor(Cloud cloud) {
		MicrosoftGraphCloud microsoftGraphCloud = (MicrosoftGraphCloud) cloud;
		return new MicrosoftGraphCloudContentRepository(microsoftGraphCloud, MicrosoftGraphClient.Companion.createInstance(context, microsoftGraphCloud.accessToken()), context);
	}
}
