package org.cryptomator.data.cloud.googledrive;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.GoogleDriveCloud;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GoogleDriveCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;
	private final GoogleDriveIdCache idCache;

	@Inject
	public GoogleDriveCloudContentRepositoryFactory(Context context, GoogleDriveIdCache idCache) {
		this.context = context;
		this.idCache = idCache;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == CloudType.GOOGLE_DRIVE;
	}

	@Override
	public CloudContentRepository<GoogleDriveCloud, GoogleDriveNode, GoogleDriveFolder, GoogleDriveFile> cloudContentRepositoryFor(Cloud cloud) {
		return new GoogleDriveCloudContentRepository(context, (GoogleDriveCloud) cloud, idCache);
	}
}
