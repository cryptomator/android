package org.cryptomator.data.cloud.webdav;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.WebDavCloud;

public class RootWebDavFolder extends WebDavFolder {

	private final WebDavCloud cloud;

	public RootWebDavFolder(WebDavCloud cloud) {
		super(null, "", "");
		this.cloud = cloud;
	}

	@Override
	public Cloud getCloud() {
		return cloud;
	}

	@Override
	public WebDavFolder withCloud(Cloud cloud) {
		return new RootWebDavFolder((WebDavCloud) cloud);
	}
}
