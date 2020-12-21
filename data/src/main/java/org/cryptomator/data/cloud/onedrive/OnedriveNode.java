package org.cryptomator.data.cloud.onedrive;

import org.cryptomator.domain.CloudNode;

public interface OnedriveNode extends CloudNode {

	boolean isFolder();

	String getName();

	String getPath();

	OnedriveFolder getParent();

}
