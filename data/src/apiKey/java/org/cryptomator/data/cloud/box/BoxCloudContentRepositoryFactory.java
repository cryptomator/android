package org.cryptomator.data.cloud.box;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.BoxCloud;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.CloudType.BOX;

@Singleton
public class BoxCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;
	private final BoxTokenStore tokenStore;

	@Inject
	public BoxCloudContentRepositoryFactory(Context context, BoxTokenStore tokenStore) {
		this.context = context;
		this.tokenStore = tokenStore;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == BOX;
	}

	@Override
	public CloudContentRepository<BoxCloud, BoxNode, BoxFolder, BoxFile> cloudContentRepositoryFor(Cloud cloud) {
		BoxCloud boxCloud = (BoxCloud) cloud;
		return new BoxCloudContentRepository(boxCloud, new BoxApi(boxCloud, tokenStore, BoxHttpClient.INSTANCE.create(context)), context);
	}

}
