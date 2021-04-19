package org.cryptomator.data.cloud.s3;

import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.cryptomator.util.Optional;

class S3CloudNodeFactory {

	public static S3File file(S3Folder parent, S3ObjectSummary file) {
		return new S3File(parent, file.getKey(), getNodePath(parent, file.getKey()), Optional.ofNullable(file.getSize()), Optional.ofNullable(file.getLastModified()));
	}

	public static S3File file(S3Folder parent, String name, Optional<Long> size) {
		return new S3File(parent, name, getNodePath(parent, name), size, Optional.empty());
	}

	public static S3File file(S3Folder folder, String name, Optional<Long> size, String path) {
		return new S3File(folder, name, path, size, Optional.empty());
	}

	public static S3Folder folder(S3Folder parent, S3ObjectSummary folder) {
		return new S3Folder(parent, folder.getKey(), getNodePath(parent, folder.getKey()));
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

	public static S3Node from(S3Folder parent, S3ObjectSummary objectSummary) {
		if (objectSummary.getKey().endsWith("/")) {
			return folder(parent, objectSummary);
		} else {
			return file(parent, objectSummary);
		}
	}

}
