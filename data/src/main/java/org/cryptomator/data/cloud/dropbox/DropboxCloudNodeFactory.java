package org.cryptomator.data.cloud.dropbox;

import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.Metadata;

import org.cryptomator.util.Optional;

class DropboxCloudNodeFactory {

	public static DropboxFile from(DropboxFolder parent, FileMetadata metadata) {
		return new DropboxFile(parent, metadata.getName(), metadata.getPathDisplay(), Optional.ofNullable(metadata.getSize()), Optional.ofNullable(metadata.getServerModified()));
	}

	public static DropboxFile file(DropboxFolder parent, String name, Optional<Long> size, String path) {
		return new DropboxFile(parent, name, path, size, Optional.empty());
	}

	public static DropboxFolder from(DropboxFolder parent, FolderMetadata metadata) {
		return new DropboxFolder(parent, metadata.getName(), getNodePath(parent, metadata.getName()));
	}

	private static String getNodePath(DropboxFolder parent, String name) {
		return parent.getPath() + "/" + name;
	}

	public static DropboxFolder folder(DropboxFolder parent, String name, String path) {
		return new DropboxFolder(parent, name, path);
	}

	public static DropboxNode from(DropboxFolder parent, Metadata metadata) {
		if (metadata instanceof FileMetadata) {
			return from(parent, (FileMetadata) metadata);
		} else {
			return from(parent, (FolderMetadata) metadata);
		}
	}

}
