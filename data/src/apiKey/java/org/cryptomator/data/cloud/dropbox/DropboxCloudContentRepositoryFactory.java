package org.cryptomator.data.cloud.dropbox;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.DropboxCloud;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.CloudType.DROPBOX;

@Singleton
public class DropboxCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;

	@Inject
	public DropboxCloudContentRepositoryFactory(Context context) {
		this.context = context;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.getType() == DROPBOX;
	}

	@Override
	public CloudContentRepository<DropboxCloud, DropboxNode, DropboxFolder, DropboxFile> cloudContentRepositoryFor(Cloud cloud) {
		return new DropboxCloudContentRepository((DropboxCloud) cloud, context);
	}

}
