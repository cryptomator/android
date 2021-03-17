package org.cryptomator.data.cloud.pcloud;

interface PCloudNode extends PCloudIdCloudNode {

	@Override
	Long getId();

	@Override
	PCloudFolder getParent();

}
