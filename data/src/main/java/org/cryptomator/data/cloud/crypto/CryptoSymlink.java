package org.cryptomator.data.cloud.crypto;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.util.Optional;

import java.util.Date;

class CryptoSymlink implements CloudFile, CryptoNode {

	private final String name;
	private final String path;
	private final String target;
	private final CloudFile cloudFile;
	private final CryptoFolder parent;

	public CryptoSymlink(CryptoFolder parent, String name, String path, String target, CloudFile cloudFile) {
		this.parent = parent;
		this.name = name;
		this.path = path;
		this.target = target;
		this.cloudFile = cloudFile;
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
	public CryptoFolder getParent() {
		return parent;
	}

	@Override
	public Optional<Long> getSize() {
		return Optional.of((long) target.length());
	}

	@Override
	public Optional<Date> getModified() {
		return cloudFile.getModified();
	}

	/**
	 * @return The actual file in the underlying, i.e. decorated, CloudContentRepository
	 */
	CloudFile getCloudFile() {
		return cloudFile;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((CryptoSymlink) obj);
	}

	private boolean internalEquals(CryptoSymlink obj) {
		return path != null && path.equals(obj.path);
	}

	@Override
	public int hashCode() {
		return path == null ? 0 : path.hashCode();
	}

}
