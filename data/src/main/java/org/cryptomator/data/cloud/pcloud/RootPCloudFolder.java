package org.cryptomator.data.cloud.pcloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.PCloud;

class RootPCloudFolder extends PCloudFolder {

	private final PCloud cloud;

	public RootPCloudFolder(PCloud cloud) {
		super(null, "", "");
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
