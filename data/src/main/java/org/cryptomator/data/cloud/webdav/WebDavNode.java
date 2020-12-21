package org.cryptomator.data.cloud.webdav;

import org.cryptomator.domain.CloudNode;

public interface WebDavNode extends CloudNode {

	@Override
	WebDavFolder getParent();
}
