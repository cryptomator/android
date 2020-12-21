package org.cryptomator.data.cloud.local.file;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;

class LocalFolder implements CloudFolder, LocalNode {

	private final LocalFolder parent;
	private final String name;
	private final String path;

	LocalFolder(LocalFolder parent, String name, String path) {
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
	public LocalFolder getParent() {
		return parent;
	}

	@Override
	public LocalFolder withCloud(Cloud cloud) {
		return new LocalFolder(parent.withCloud(cloud), name, path);
	}
}
