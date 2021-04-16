package org.cryptomator.data.cloud.s3;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.S3Cloud;

class RootS3Folder extends S3Folder {

	private final S3Cloud cloud;

	public RootS3Folder(S3Cloud cloud) {
		super(null, "", "");
		this.cloud = cloud;
	}

	@Override
	public S3Cloud getCloud() {
		return cloud;
	}

	@Override
	public S3Folder withCloud(Cloud cloud) {
		return new RootS3Folder((S3Cloud) cloud);
	}
}
