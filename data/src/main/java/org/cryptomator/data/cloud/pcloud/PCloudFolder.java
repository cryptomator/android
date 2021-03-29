package org.cryptomator.data.cloud.pcloud;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;

class PCloudFolder implements CloudFolder, PCloudNode {

	private final PCloudFolder parent;
	private final String name;
	private final String path;

	public PCloudFolder(PCloudFolder parent, String name, String path) {
		this.parent = parent;
		this.name = name;
		this.path = path;
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
	public PCloudFolder getParent() {
		return parent;
	}

	@Override
	public PCloudFolder withCloud(Cloud cloud) {
		return new PCloudFolder(parent.withCloud(cloud), name, path);
	}
}
