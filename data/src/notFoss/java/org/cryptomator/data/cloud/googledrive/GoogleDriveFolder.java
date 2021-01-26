package org.cryptomator.data.cloud.googledrive;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;

class GoogleDriveFolder implements CloudFolder, GoogleDriveNode {

	private final GoogleDriveFolder parent;
	private final String name;
	private final String path;
	private final String driveId;

	public GoogleDriveFolder(GoogleDriveFolder parent, String name, String path, String driveId) {
		this.parent = parent;
		this.name = name;
		this.path = path;
		this.driveId = driveId;
	}

	@Override
	public Cloud getCloud() {
		return parent.getCloud();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getPath() {
		return path;
	}

	@Override
	public String getDriveId() {
		return driveId;
	}

	@Override
	public GoogleDriveFolder getParent() {
		return parent;
	}

	@Override
	public GoogleDriveFolder withCloud(Cloud cloud) {
		return new GoogleDriveFolder(parent.withCloud(cloud), name, path, driveId);
	}
}
