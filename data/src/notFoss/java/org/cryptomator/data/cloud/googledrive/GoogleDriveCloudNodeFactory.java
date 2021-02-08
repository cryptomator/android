package org.cryptomator.data.cloud.googledrive;

import com.google.api.services.drive.model.File;

import org.cryptomator.util.Optional;

import java.util.Date;

class GoogleDriveCloudNodeFactory {

	public static GoogleDriveFile file(GoogleDriveFolder parent, File file) {
		return new GoogleDriveFile(parent, file.getName(), getNodePath(parent, file.getName()), file.getId(), getFileSize(file), getModified(file));
	}

	public static GoogleDriveFile file(GoogleDriveFolder parent, String name, Optional<Long> size) {
		return new GoogleDriveFile(parent, name, getNodePath(parent, name), null, size, Optional.empty());
	}

	private static Optional<Date> getModified(File file) {
		return file.getModifiedTime() != null ? Optional.of(new Date(file.getModifiedTime().getValue())) : Optional.empty();
	}

	private static Optional<Long> getFileSize(File file) {
		return file.getSize() != null ? Optional.of(file.getSize()) : Optional.empty();
	}

	public static GoogleDriveFile file(GoogleDriveFolder parent, String name, Optional<Long> size, String path, String driveId) {
		return new GoogleDriveFile(parent, name, path, driveId, size, Optional.empty());
	}

	public static GoogleDriveFolder folder(GoogleDriveFolder parent, File file) {
		return new GoogleDriveFolder(parent, file.getName(), getNodePath(parent, file.getName()), file.getId());
	}

	public static GoogleDriveFolder folder(GoogleDriveFolder parent, String name) {
		return new GoogleDriveFolder(parent, name, getNodePath(parent, name), null);
	}

	public static GoogleDriveFolder folder(GoogleDriveFolder parent, String name, String path, String driveId) {
		return new GoogleDriveFolder(parent, name, path, driveId);
	}

	public static GoogleDriveNode from(GoogleDriveFolder parent, File file) {
		if (isFolder(file)) {
			return folder(parent, file);
		} else {
			return file(parent, file);
		}
	}

	public static boolean isFolder(File file) {
		return file.getMimeType().equals("application/vnd.google-apps.folder");
	}

	public static String getNodePath(GoogleDriveFolder parent, String name) {
		return parent.getPath() + "/" + name;
	}

}
