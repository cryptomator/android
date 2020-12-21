package org.cryptomator.domain.usecases;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;

import java.util.ArrayList;
import java.util.List;

public class CloudFolderRecursiveListing {

	private final CloudFolder parent;
	private final List<CloudFile> files;
	private final List<CloudFolderRecursiveListing> folders;

	public CloudFolderRecursiveListing(CloudFolder parent) {
		this.parent = parent;
		this.files = new ArrayList<>();
		this.folders = new ArrayList<>();
	}

	public CloudFolder getParent() {
		return parent;
	}

	public List<CloudFile> getFiles() {
		return files;
	}

	public List<CloudFolderRecursiveListing> getFolders() {
		return folders;
	}

	public void addFile(CloudFile file) {
		this.files.add(file);
	}

	public void addFolders(CloudFolderRecursiveListing folder) {
		this.folders.add(folder);
	}
}
