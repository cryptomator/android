package org.cryptomator.data.cloud.s3;

import org.cryptomator.domain.CloudNode;

interface S3Node extends CloudNode {

	@Override
	S3Folder getParent();

	String getKey();

}
