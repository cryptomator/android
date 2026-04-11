package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFolder;

public interface FolderCreatedCallback {

	void onFolderCreated(String relativePath, CloudFolder folder);

	FolderCreatedCallback NO_OP = (relativePath, folder) -> {
	};
}
