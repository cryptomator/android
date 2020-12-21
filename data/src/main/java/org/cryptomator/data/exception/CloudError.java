package org.cryptomator.data.exception;

public enum CloudError {

	CREATE_FOLDER("Cannot create folder."),

	LIST_NODES("Cannot retrieve cloud content."),

	RENAME_FOLDER("Cannot rename folder."),

	RENAME_FILE("Cannot rename file."),

	DELETE_PATH("Cannot delete file/folder."),

	UPLOAD_FILE("Cannot upload file."),

	DOWNLOAD_FILE("Cannot download file."),

	CURRENT_ACCOUNT("Cannot retrieve account information."),

	MOVE_PATH("Cannot move file/folder."),

	TARGET_EXISTS("");

	private final String errorMessage;

	CloudError(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
