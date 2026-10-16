package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

import timber.log.Timber;

@UseCase
class DeleteNodes {

	private final CloudContentRepository cloudContentRepository;
	private final VaultDocumentRepository vaultDocumentRepository;
	private final List<CloudNode> cloudNodes;

	public DeleteNodes(CloudContentRepository cloudContentRepository, //
			VaultDocumentRepository vaultDocumentRepository, //
			@Parameter List<CloudNode> cloudNodes) {
		this.cloudContentRepository = cloudContentRepository;
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.cloudNodes = cloudNodes;
	}

	public List<CloudNode> execute() throws BackendException {
		for (CloudNode cloudNode : cloudNodes) {
			try {
				VaultPath vaultNode = vaultDocumentRepository.pathOf(cloudNode);
				if (vaultNode != null) {
					vaultDocumentRepository.delete(vaultNode).await();
				} else {
					cloudContentRepository.delete(cloudNode);
				}
			} catch (NoSuchCloudFileException e) {
				Timber.tag("DeleteNodes").i("Skipped node deletion: Not found");
				Timber.tag("DeleteNodes").v(e, "Skipped deletion of %s: Not found", cloudNode.getPath());
			}
		}
		return cloudNodes;
	}
}
