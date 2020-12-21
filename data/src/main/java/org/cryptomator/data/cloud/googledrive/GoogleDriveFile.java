package org.cryptomator.data.cloud.googledrive;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.util.Optional;

import java.util.Date;

class GoogleDriveFile implements CloudFile, GoogleDriveNode {

	private final GoogleDriveFolder parent;
	private final String name;
	private final String path;
	private final String driveId;
	private final Optional<Long> size;
	private final Optional<Date> modified;

	public GoogleDriveFile(GoogleDriveFolder parent, String name, String path, String driveId, Optional<Long> size, Optional<Date> modified) {
		this.parent = parent;
		this.name = name;
		this.path = path;
		this.driveId = driveId;
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
	public String getDriveId() {
		return driveId;
	}

	@Override
	public GoogleDriveFolder getParent() {
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
