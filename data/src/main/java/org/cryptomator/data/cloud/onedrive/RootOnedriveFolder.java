package org.cryptomator.data.cloud.onedrive;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.OnedriveCloud;

class RootOnedriveFolder extends OnedriveFolder {

	private final OnedriveCloud oneDriveCloud;

	public RootOnedriveFolder(OnedriveCloud oneDriveCloud) {
		super(null, "", "");
		this.oneDriveCloud = oneDriveCloud;
	}

	@Override
	public OnedriveCloud getCloud() {
		return oneDriveCloud;
	}

	@Override
	public RootOnedriveFolder withCloud(Cloud cloud) {
		return new RootOnedriveFolder((OnedriveCloud) cloud);
	}
}
