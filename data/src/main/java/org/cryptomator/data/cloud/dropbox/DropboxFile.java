package org.cryptomator.data.cloud.dropbox;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.util.Optional;

import java.util.Date;

class DropboxFile implements CloudFile, DropboxNode {

	private final DropboxFolder parent;
	private final String name;
	private final String path;
	private final Optional<Long> size;
	private final Optional<Date> modified;

	public DropboxFile(DropboxFolder parent, String name, String path, Optional<Long> size, Optional<Date> modified) {
		this.parent = parent;
		this.name = name;
		this.path = path;
		this.size = size;
		this.modified = modified;
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
	public DropboxFolder getParent() {
		return parent;
	}

	@Override
	public Optional<Long> getSize() {
		return size;
	}

	@Override
	public Optional<Date> getModified() {
		return modified;
	}

}
