package org.cryptomator.data.cloud.s3;

import com.pcloud.sdk.RemoteEntry;
import com.pcloud.sdk.RemoteFile;
import com.pcloud.sdk.RemoteFolder;

import org.cryptomator.util.Optional;

class S3CloudNodeFactory {

	public static S3File file(S3Folder parent, RemoteFile file) {
		return new S3File(parent, file.name(), getNodePath(parent, file.name()), Optional.ofNullable(file.size()), Optional.ofNullable(file.lastModified()));
	}

	public static S3File file(S3Folder parent, String name, Optional<Long> size) {
		return new S3File(parent, name, getNodePath(parent, name), size, Optional.empty());
	}

	public static S3File file(S3Folder folder, String name, Optional<Long> size, String path) {
		return new S3File(folder, name, path, size, Optional.empty());
	}

	public static S3Folder folder(S3Folder parent, RemoteFolder folder) {
		return new S3Folder(parent, folder.name(), getNodePath(parent, folder.name()));
	}

	public static S3Folder folder(S3Folder parent, String name) {
		return new S3Folder(parent, name, getNodePath(parent, name));
	}

	public static S3Folder folder(S3Folder parent, String name, String path) {
		return new S3Folder(parent, name, path);
	}

	public static String getNodePath(S3Folder parent, String name) {
		return parent.getPath() + "/" + name;
	}

	public static S3Node from(S3Folder parent, RemoteEntry remoteEntry) {
		if (remoteEntry instanceof RemoteFile) {
			return file(parent, remoteEntry.asFile());
		} else {
			return folder(parent, remoteEntry.asFolder());
		}
	}

}
