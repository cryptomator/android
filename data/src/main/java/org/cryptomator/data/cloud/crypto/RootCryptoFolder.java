package org.cryptomator.data.cloud.crypto;

import org.cryptomator.domain.Cloud;

class RootCryptoFolder extends CryptoFolder {

	private final CryptoCloud cloud;

	public RootCryptoFolder(CryptoCloud cloud) {
		super(null, "", "", null);
		this.cloud = cloud;
	}

	public static boolean isRoot(CryptoFolder folder) {
		return folder instanceof RootCryptoFolder;
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
