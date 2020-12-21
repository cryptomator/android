package org.cryptomator.domain.usecases;

import java.util.ArrayList;
import java.util.List;

public class CloudNodeRecursiveListing {

	private final List<CloudFolderRecursiveListing> foldersContent;

	public CloudNodeRecursiveListing(int size) {
		this.foldersContent = new ArrayList<>(size);
	}

	public void addFolderContent(CloudFolderRecursiveListing cloudFolderRecursiveListing) {
		foldersContent.add(cloudFolderRecursiveListing);
	}

	public List<CloudFolderRecursiveListing> getFoldersContent() {
		return foldersContent;
	}
}
