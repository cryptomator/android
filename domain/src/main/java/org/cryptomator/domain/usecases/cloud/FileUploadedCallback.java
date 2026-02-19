package org.cryptomator.domain.usecases.cloud;

public interface FileUploadedCallback {

	void onFileUploaded(String relativePath);

	FileUploadedCallback NO_OP = relativePath -> {
	};
}
