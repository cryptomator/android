package org.cryptomator.data.cloud.pcloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.PCloudCloud;

class RootPCloudFolder extends PCloudFolder {

	private final PCloudCloud cloud;
	private static final long rootFolderId = 0L;

	public RootPCloudFolder(PCloudCloud cloud) {
		super(null, "", "", rootFolderId);
		this.cloud = cloud;
	}

	@Override
	public PCloudCloud getCloud() {
		return cloud;
	}

	@Override
	public PCloudFolder withCloud(Cloud cloud) {
		return new RootPCloudFolder((PCloudCloud) cloud);
	}
}
