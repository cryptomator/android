package org.cryptomator.data.cloud.googledrive;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.GoogleDriveCloud;

public class RootGoogleDriveFolder extends GoogleDriveFolder {

	private final GoogleDriveCloud cloud;

	public RootGoogleDriveFolder(GoogleDriveCloud cloud) {
		super(null, "", "", "root");
		this.cloud = cloud;
	}

	@Override
	public GoogleDriveCloud getCloud() {
		return cloud;
	}

	@Override
	public GoogleDriveFolder withCloud(Cloud cloud) {
		return new RootGoogleDriveFolder((GoogleDriveCloud) cloud);
	}
}
