package org.cryptomator.data.cloud.pcloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.PCloud;

class RootPCloudFolder extends PCloudFolder {

	private final PCloud cloud;
	private static final long rootFolderId = 0L;

	public RootPCloudFolder(PCloud cloud) {
		super(null, "", "", rootFolderId);
		this.cloud = cloud;
	}

	@Override
	public PCloud getCloud() {
		return cloud;
	}

	@Override
	public PCloudFolder withCloud(Cloud cloud) {
		return new RootPCloudFolder((PCloud) cloud);
	}
}
