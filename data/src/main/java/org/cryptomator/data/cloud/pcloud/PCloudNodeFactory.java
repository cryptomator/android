package org.cryptomator.data.cloud.pcloud;

import com.pcloud.sdk.RemoteEntry;
import com.pcloud.sdk.RemoteFile;
import com.pcloud.sdk.RemoteFolder;

import org.cryptomator.util.Optional;

class PCloudNodeFactory {

	public static PCloudFile file(PCloudFolder parent, RemoteFile file) {
		return new PCloudFile(parent, file.name(), getNodePath(parent, file.name()), Optional.ofNullable(file.size()), Optional.ofNullable(file.lastModified()));
	}

	public static PCloudFile file(PCloudFolder parent, String name, Optional<Long> size) {
		return new PCloudFile(parent, name, getNodePath(parent, name), size, Optional.empty());
	}

	public static PCloudFile file(PCloudFolder parent, String name, Optional<Long> size, String path) {
		return new PCloudFile(parent, name, path, size, Optional.empty());
	}

	public static PCloudFolder folder(PCloudFolder parent, RemoteFolder folder) {
		return new PCloudFolder(parent, folder.name(), getNodePath(parent, folder.name()));
	}

	public static PCloudFolder folder(PCloudFolder parent, String name) {
		return new PCloudFolder(parent, name, getNodePath(parent, name));
	}

	public static PCloudFolder folder(PCloudFolder parent, String name, String path) {
		return new PCloudFolder(parent, name, path);
	}

	public static String getNodePath(PCloudFolder parent, String name) {
		return parent.getPath() + "/" + name;
	}

	public static PCloudNode from(PCloudFolder parent, RemoteEntry remoteEntry) {
		if (remoteEntry instanceof RemoteFile) {
			return file(parent, remoteEntry.asFile());
		} else {
			return folder(parent, remoteEntry.asFolder());
		}
	}

}
