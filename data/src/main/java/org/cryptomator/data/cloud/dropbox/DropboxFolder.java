package org.cryptomator.data.cloud.dropbox;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;

class DropboxFolder implements CloudFolder, DropboxNode {

	private final DropboxFolder parent;
	private final String name;
	private final String path;

	public DropboxFolder(DropboxFolder parent, String name, String path) {
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
	public DropboxFolder getParent() {
		return parent;
	}

	@Override
	public DropboxFolder withCloud(Cloud cloud) {
		return new DropboxFolder(parent.withCloud(cloud), name, path);
	}
}
