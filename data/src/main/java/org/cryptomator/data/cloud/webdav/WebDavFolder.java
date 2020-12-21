package org.cryptomator.data.cloud.webdav;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;
import org.jetbrains.annotations.NotNull;

import static java.lang.String.format;

public class WebDavFolder implements CloudFolder, WebDavNode {

	private final WebDavFolder parent;
	private final String name;
	private final String path;

	public WebDavFolder(WebDavFolder parent, String name) {
		this(parent, name, parent.getPath() + "/" + name);
	}

	public WebDavFolder(WebDavFolder parent, String name, String path) {
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
	public WebDavFolder getParent() {
		return parent;
	}

	@Override
	public WebDavFolder withCloud(Cloud cloud) {
		return new WebDavFolder(parent.withCloud(cloud), name, path);
	}

	@NotNull
	@Override
	public String toString() {
		return format("WebDavFolder(%s)", path);
	}
}
