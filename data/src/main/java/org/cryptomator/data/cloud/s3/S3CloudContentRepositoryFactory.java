package org.cryptomator.data.cloud.s3;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.S3Cloud;
import org.cryptomator.domain.repository.CloudContentRepository;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.domain.CloudType.S3;

@Singleton
public class S3CloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;

	@Inject
	public S3CloudContentRepositoryFactory(Context context) {
		this.context = context;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == S3;
	}

	@Override
	public CloudContentRepository<S3Cloud, S3Node, S3Folder, S3File> cloudContentRepositoryFor(Cloud cloud) {
		return new S3CloudContentRepository((S3Cloud) cloud, context);
	}

}
