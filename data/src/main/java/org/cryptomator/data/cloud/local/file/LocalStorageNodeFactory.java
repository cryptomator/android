package org.cryptomator.data.cloud.local.file;

import org.cryptomator.util.Optional;

import java.io.File;
import java.util.Date;

class LocalStorageNodeFactory {

	public static LocalNode from(LocalFolder parent, File file) {
		if (file.isDirectory()) {
			return folder(parent, file);
		} else {
			return file( //
					parent, //
					file.getName(), //
					file.getPath(), //
					Optional.of(file.length()), //
					Optional.of(new Date(file.lastModified())));
		}
	}

	public static LocalFolder folder(LocalFolder parent, File file) {
		return folder(parent, file.getName(), file.getPath());
	}

	public static LocalFolder folder(LocalFolder parent, String name, String path) {
		return new LocalFolder(parent, name, path);
	}

	public static LocalFile file(LocalFolder folder, String name, String path, Optional<Long> size, Optional<Date> modified) {
		return new LocalFile(folder, name, path, size, modified);
	}
}
