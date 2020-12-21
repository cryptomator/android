package org.cryptomator.data.cloud.onedrive;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;

class OnedriveFolder implements CloudFolder, OnedriveNode {

	private final OnedriveFolder parent;
	private final String name;
	private final String path;

	public OnedriveFolder(OnedriveFolder parent, String name, String path) {
		this.parent = parent;
		this.name = name;
		this.path = path;
	}

	@Override
	public boolean isFolder() {
		return true;
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
	public OnedriveFolder getParent() {
		return parent;
	}

	@Override
	public OnedriveFolder withCloud(Cloud cloud) {
		return new OnedriveFolder(parent.withCloud(cloud), name, path);
	}
}
