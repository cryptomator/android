package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.usecases.CloudFolderRecursiveListing;
import org.cryptomator.domain.usecases.CloudNodeRecursiveListing;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
class GetCloudListRecursive {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final List<CloudFolder> folders;

	GetCloudListRecursive(CloudContentRepository cloudContentRepository, //
			VaultDocumentRepository vaultDocumentRepository, //
			@Parameter List<CloudFolder> folders) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
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
		List<CloudNode> children = list(folder);
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

	/**
	 * A folder of a vault is listed through the vault's cache, so that files whose upload is still pending or has failed
	 * are included, as the file browser shows them.
	 */
	private List<CloudNode> list(CloudFolder folder) throws BackendException {
		if (vaultDocumentRepository.pathOf(folder) != null) {
			return vaultDocumentRepository.list(folder, true).getNodes();
		}
		return cloudContentRepository.list(folder);
	}

}
