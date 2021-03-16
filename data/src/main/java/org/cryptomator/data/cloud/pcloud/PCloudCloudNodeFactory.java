package org.cryptomator.data.cloud.pcloud;

import com.pcloud.sdk.RemoteEntry;
import com.pcloud.sdk.RemoteFile;
import com.pcloud.sdk.RemoteFolder;

import org.cryptomator.util.Optional;

class PCloudCloudNodeFactory {

	public static PCloudFile file(PCloudFolder parent, RemoteFile metadata) {
		return new PCloudFile(parent, metadata.fileId(), metadata.name(), getNodePath(parent, metadata.name()), Optional.ofNullable(metadata.size()), Optional.ofNullable(metadata.lastModified()));
	}

	public static PCloudFile file(PCloudFolder parent, String name, Optional<Long> size, String path) {
		return new PCloudFile(parent, null, name, path, size, Optional.empty());
	}

	public static PCloudFile file(PCloudFolder parent, String name, Optional<Long> size) {
		return new PCloudFile(parent, null, name, getNodePath(parent, name), size, Optional.empty());
	}

	public static PCloudFile file(PCloudFolder folder, String name, Optional<Long> size, String path, Long fileId) {
		return new PCloudFile(folder, fileId, name, path, size, Optional.empty());
	}

	public static PCloudFolder folder(PCloudFolder parent, RemoteFolder metadata) {
		return new PCloudFolder(parent, metadata.folderId(), metadata.name(), getNodePath(parent, metadata.name()));
	}

	public static PCloudFolder folder(PCloudFolder parent, String name, String path) {
		return new PCloudFolder(parent, null, name, path);
	}

	public static PCloudFolder folder(PCloudFolder parent, String name) {
		return new PCloudFolder(parent, null, name, getNodePath(parent, name));
	}

	public static PCloudFolder folder(PCloudFolder parent, String name, String path, Long folderId) {
		return new PCloudFolder(parent, folderId, name, path);
	}

	public static String getNodePath(PCloudFolder parent, String name) {
		return parent.getPath() + "/" + name;
	}

	public static PCloudNode from(PCloudFolder parent, RemoteEntry metadata) {
		if (metadata instanceof RemoteFile) {
			return file(parent, metadata.asFile());
		} else {
			return folder(parent, metadata.asFolder());
		}
	}

}
