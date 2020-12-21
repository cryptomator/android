package org.cryptomator.data.cloud.dropbox;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.DropboxCloud;

class RootDropboxFolder extends DropboxFolder {

	private final DropboxCloud cloud;

	public RootDropboxFolder(DropboxCloud cloud) {
		super(null, "", "");
		this.cloud = cloud;
	}

	@Override
	public DropboxCloud getCloud() {
		return cloud;
	}

	@Override
	public DropboxFolder withCloud(Cloud cloud) {
		return new RootDropboxFolder((DropboxCloud) cloud);
	}
}
