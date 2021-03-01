package org.cryptomator.data.cloud.crypto;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;

class CryptoFolder implements CloudFolder, CryptoNode {

	private final String name;
	private final String path;
	private final CryptoFolder parent;
	private final CloudFile dirFile;

	CryptoFolder(CryptoFolder parent, String name, String path, CloudFile dirFile) {
		this.parent = parent;
		this.name = name;
		this.path = path;
		this.dirFile = dirFile;
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

	/**
	 * @return the file containing the directory id, in the underlying, i.e. decorated, CloudContentRepository
	 */
	CloudFile getDirFile() {
		return dirFile;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		if (obj == this) {
			return true;
		}
		return internalEquals((CryptoFolder) obj);
	}

	private boolean internalEquals(CryptoFolder obj) {
		return path != null && path.equals(obj.path);
	}

	@Override
	public int hashCode() {
		return path == null ? 0 : path.hashCode();
	}

	@Override
	public CryptoFolder withCloud(Cloud cloud) {
		return new CryptoFolder(parent.withCloud(cloud), name, path, dirFile);
	}
}
