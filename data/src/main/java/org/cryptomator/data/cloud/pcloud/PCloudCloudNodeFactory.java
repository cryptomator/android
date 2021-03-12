package org.cryptomator.data.cloud.pcloud;

import com.pcloud.sdk.RemoteEntry;
import com.pcloud.sdk.RemoteFile;
import com.pcloud.sdk.RemoteFolder;

import org.cryptomator.util.Optional;

class PCloudCloudNodeFactory {

	public static PCloudFile from(PCloudFolder parent, RemoteFile metadata) {

		return new PCloudFile(parent, metadata.fileId(), metadata.name(), getNodePath(parent, metadata.name()), Optional.ofNullable(metadata.size()), Optional.ofNullable(metadata.lastModified()));
	}

	public static PCloudFile file(PCloudFolder parent, String name, Optional<Long> size, String path) {
		return new PCloudFile(parent, null, name, path, size, Optional.empty());
	}

	public static PCloudFolder from(PCloudFolder parent, RemoteFolder metadata) {
		return new PCloudFolder(parent, metadata.folderId(), metadata.name(), getNodePath(parent, metadata.name()));
	}

	private static String getNodePath(PCloudFolder parent, String name) {
		return parent.getPath() + "/" + name;
	}

	public static PCloudFolder folder(PCloudFolder parent, String name, String path) {
		return new PCloudFolder(parent, null, name, path);
	}

	public static PCloudNode from(PCloudFolder parent, RemoteEntry metadata) {
		if (metadata instanceof RemoteFile) {
			return from(parent, (RemoteFile) metadata);
		} else {
			return from(parent, (RemoteFolder) metadata);
		}
	}

}
