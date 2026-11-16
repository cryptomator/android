package org.cryptomator.domain.usecases.cloud;

public interface FileUploadedCallback {

	/**
	 * Called when the file at {@code relativePath} has been uploaded, or skipped because its name was taken.
	 */
	void onFileUploaded(String relativePath);

	FileUploadedCallback NO_OP = relativePath -> {
	};
}
