package org.cryptomator.data.cloud.s3;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFolder;

class S3Folder implements CloudFolder, S3Node {

	private static final String DELIMITER = "/";

	private final S3Folder parent;
	private final String name;
	private final String path;

	public S3Folder(S3Folder parent, String name, String path) {
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
	public String getKey() {
		if (path.startsWith(DELIMITER)) {
			return path.substring(DELIMITER.length()) + DELIMITER;
		}
		return path + DELIMITER;
	}

	@Override
	public S3Folder getParent() {
		return parent;
	}

	@Override
	public S3Folder withCloud(Cloud cloud) {
		return new S3Folder(parent.withCloud(cloud), name, path);
	}
}
