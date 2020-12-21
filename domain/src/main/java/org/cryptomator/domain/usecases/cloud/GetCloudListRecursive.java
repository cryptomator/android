package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.CloudFolderRecursiveListing;
import org.cryptomator.domain.usecases.CloudNodeRecursiveListing;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class GetCloudListRecursive {

	private final CloudContentRepository cloudContentRepository;
	private final List<CloudFolder> folders;

	GetCloudListRecursive(CloudContentRepository cloudContentRepository, //
			@Parameter List<CloudFolder> folders) {
		this.cloudContentRepository = cloudContentRepository;
		this.folders = folders;
	}

	public CloudNodeRecursiveListing execute() throws BackendException {
		CloudNodeRecursiveListing cloudNodeRecursiveListing = new CloudNodeRecursiveListing(folders.size());
		for (CloudFolder folder : folders) {
			cloudNodeRecursiveListing.addFolderContent(recursiveListing(new CloudFolderRecursiveListing(folder), folder));
		}
		return cloudNodeRecursiveListing;
	}

	private CloudFolderRecursiveListing recursiveListing(CloudFolderRecursiveListing cloudFolderRecursiveListing, CloudFolder folder) throws BackendException {
		List<CloudNode> children = cloudContentRepository.list(folder);
		for (CloudNode child : children) {
			if (child instanceof CloudFolder) {
				cloudFolderRecursiveListing.addFolders(//
						recursiveListing(new CloudFolderRecursiveListing((CloudFolder) child), //
								(CloudFolder) child));
			} else if (child instanceof CloudFile) {
				cloudFolderRecursiveListing.addFile((CloudFile) child);
			}
		}
		return cloudFolderRecursiveListing;
	}

}
