package org.cryptomator.data.cloud.googledrive;

interface GoogleDriveNode extends GoogleDriveIdCloudNode {

	@Override
	String getDriveId();

	@Override
	GoogleDriveFolder getParent();
}
