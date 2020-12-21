package org.cryptomator.data.cloud.onedrive;

import com.microsoft.graph.models.extensions.DriveItem;

import org.cryptomator.util.Optional;

import java.util.Date;

class OnedriveCloudNodeFactory {

	public static OnedriveNode from(OnedriveFolder parent, DriveItem item) {
		if (isFolder(item)) {
			return folder(parent, item);
		} else {
			return file(parent, item);
		}
	}

	private static OnedriveFile file(OnedriveFolder parent, DriveItem item) {
		return new OnedriveFile(parent, item.name, getNodePath(parent, item.name), Optional.ofNullable(item.size), lastModified(item));
	}

	public static OnedriveFile file(OnedriveFolder parent, DriveItem item, Optional<Date> lastModified) {
		return new OnedriveFile(parent, item.name, getNodePath(parent, item.name), Optional.ofNullable(item.size), lastModified);
	}

	public static OnedriveFile file(OnedriveFolder parent, String name, Optional<Long> size) {
		return new OnedriveFile(parent, name, getNodePath(parent, name), size, Optional.empty());
	}

	public static OnedriveFile file(OnedriveFolder parent, String name, Optional<Long> size, String path) {
		return new OnedriveFile(parent, name, path, size, Optional.empty());
	}

	public static OnedriveFolder folder(OnedriveFolder parent, DriveItem item) {
		return new OnedriveFolder(parent, item.name, getNodePath(parent, item.name));
	}

	public static OnedriveFolder folder(OnedriveFolder parent, String name) {
		return new OnedriveFolder(parent, name, getNodePath(parent, name));
	}

	public static OnedriveFolder folder(OnedriveFolder parent, String name, String path) {
		return new OnedriveFolder(parent, name, path);
	}

	private static String getNodePath(OnedriveFolder parent, String name) {
		return parent.getPath() + "/" + name;
	}

	public static String getId(DriveItem item) {
		return item.remoteItem != null //
				? item.remoteItem.id //
				: item.id;
	}

	public static String getDriveId(DriveItem item) {
		return item.remoteItem != null //
				? item.remoteItem.parentReference.driveId //
				: item.parentReference != null //
						? item.parentReference.driveId //
						: null;
	}

	public static boolean isFolder(DriveItem item) {
		return item.folder != null || (item.remoteItem != null && item.remoteItem.folder != null);
	}

	private static Optional<Date> lastModified(DriveItem item) {
		if (item.lastModifiedDateTime == null) {
			return Optional.empty();
		} else {
			return Optional.of(item.lastModifiedDateTime.getTime());
		}
	}

}
