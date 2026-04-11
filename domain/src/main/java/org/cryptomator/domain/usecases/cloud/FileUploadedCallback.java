package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;

public interface FileUploadedCallback {

	void onFileUploaded(String relativePath, CloudFile file);

	FileUploadedCallback NO_OP = (relativePath, file) -> {
	};
}
