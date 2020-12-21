package org.cryptomator.data.cloud.crypto;

import org.cryptomator.domain.Cloud;

class RootCryptoFolder extends CryptoFolder {

	public static boolean isRoot(CryptoFolder folder) {
		return folder instanceof RootCryptoFolder;
	}

	private final CryptoCloud cloud;

	public RootCryptoFolder(CryptoCloud cloud) {
		super(null, "", "", null);
		this.cloud = cloud;
	}

	@Override
	public Cloud getCloud() {
		return cloud;
	}

	@Override
	public CryptoFolder withCloud(Cloud cloud) {
		return new RootCryptoFolder((CryptoCloud) cloud);
	}
}
