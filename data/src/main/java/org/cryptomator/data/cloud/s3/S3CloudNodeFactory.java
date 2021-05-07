package org.cryptomator.data.cloud.s3;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3ObjectSummary;

import org.cryptomator.util.Optional;

import java.util.Date;

class S3CloudNodeFactory {

	private static final String DELIMITER = "/";

	public static S3File file(S3Folder parent, S3ObjectSummary file) {
		String name = getNameFromKey(file.getKey());
		return new S3File(parent, name, getNodePath(parent, name), Optional.ofNullable(file.getSize()), Optional.ofNullable(file.getLastModified()));
	}

	public static S3File file(S3Folder parent, String name, ObjectMetadata file) {
		return new S3File(parent, name, getNodePath(parent, name), Optional.ofNullable(file.getContentLength()), Optional.ofNullable(file.getLastModified()));
	}


	public static S3File file(S3Folder parent, String name, Optional<Long> size) {
		return new S3File(parent, name, getNodePath(parent, name), size, Optional.empty());
	}

	public static S3File file(S3Folder parent, String name, Optional<Long> size, String path) {
		return new S3File(parent, name, path, size, Optional.empty());
	}

	public static S3File file(S3Folder parent, String name, Optional<Long> size, Optional<Date> lastModified) {
		return new S3File(parent, name, getNodePath(parent, name), size, lastModified);
	}

	public static S3Folder folder(S3Folder parent, S3ObjectSummary folder) {
		String name = getNameFromKey(folder.getKey());
		return new S3Folder(parent, name, getNodePath(parent, name));
	}

	public static S3Folder folder(S3Folder parent, String name) {
		return new S3Folder(parent, name, getNodePath(parent, name));
	}

	public static S3Folder folder(S3Folder parent, String name, String path) {
		return new S3Folder(parent, name, path);
	}

	private static String getNodePath(S3Folder parent, String name) {
		return parent.getKey() + name;
	}

	public static String getNameFromKey(String key) {
		String name = key;
		if (key.endsWith(DELIMITER)) {
			name = key.substring(0, key.length() -1);
		}
		return name.contains(DELIMITER) ? name.substring(name.lastIndexOf(DELIMITER) + 1) : name;
	}

	public static S3Node from(S3Folder parent, S3ObjectSummary objectSummary) {
		if (objectSummary.getKey().endsWith(DELIMITER)) {
			return folder(parent, objectSummary);
		} else {
			return file(parent, objectSummary);
		}
	}

}
