package org.cryptomator.data.cloud.pcloud;

import com.google.api.services.drive.model.File;
import com.pcloud.sdk.RemoteEntry;
import com.pcloud.sdk.RemoteFile;
import com.pcloud.sdk.RemoteFolder;

import org.cryptomator.util.Optional;

import java.util.Date;

class PCloudCloudNodeFactory {

	public static PCloudFile file(PCloudFolder parent, RemoteFile file) {
		return new PCloudFile(parent, file.name(), getNodePath(parent, file.name()), file.fileId(), Optional.ofNullable(file.size()), Optional.ofNullable(file.lastModified()));
	}

	public static PCloudFile file(PCloudFolder parent, String name, Optional<Long> size) {
		return new PCloudFile(parent, name, getNodePath(parent, name), null, size, Optional.empty());
	}

	public static PCloudFile file(PCloudFolder folder, String name, Optional<Long> size, String path, Long fileId) {
		return new PCloudFile(folder, name, path, fileId, size, Optional.empty());
	}

	public static PCloudFolder folder(PCloudFolder parent, RemoteFolder folder) {
		return new PCloudFolder(parent, folder.name(), getNodePath(parent, folder.name()), folder.folderId());
	}

	public static PCloudFolder folder(PCloudFolder parent, String name) {
		return new PCloudFolder(parent, name, getNodePath(parent, name), null);
	}

	public static PCloudFolder folder(PCloudFolder parent, String name, String path, Long folderId) {
		return new PCloudFolder(parent, name, path, folderId);
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
