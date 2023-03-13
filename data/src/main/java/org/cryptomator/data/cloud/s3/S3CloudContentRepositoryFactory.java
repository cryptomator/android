package org.cryptomator.data.cloud.s3;

import static org.cryptomator.domain.CloudType.S3;

import android.content.Context;

import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.S3Cloud;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.util.SharedPreferencesHandler;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class S3CloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Context context;
	private final SharedPreferencesHandler sharedPreferencesHandler;

	@Inject
	public S3CloudContentRepositoryFactory(Context context, SharedPreferencesHandler sharedPreferencesHandler) {
		this.context = context;
		this.sharedPreferencesHandler = sharedPreferencesHandler;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == S3;
	}

	@Override
	public CloudContentRepository<S3Cloud, S3Node, S3Folder, S3File> cloudContentRepositoryFor(Cloud cloud) {
		S3Cloud s3cloud = (S3Cloud) cloud;
		return new S3CloudContentRepository(s3cloud, S3Client.Companion.createClient(s3cloud, context, sharedPreferencesHandler), context);
	}

}
