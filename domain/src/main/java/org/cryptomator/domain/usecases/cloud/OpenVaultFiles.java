package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.VaultPath;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.VaultDocumentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;

@UseCase
class OpenVaultFiles {

	private final VaultDocumentRepository vaultDocumentRepository;
	private final List<CloudFile> files;

	public OpenVaultFiles(VaultDocumentRepository vaultDocumentRepository, @Parameter List<CloudFile> files) {
		this.vaultDocumentRepository = vaultDocumentRepository;
		this.files = files;
	}

	/**
	 * @return the documentIds of the files in the documents provider
	 */
	public List<String> execute(ProgressAware<DownloadState> progressAware) throws BackendException {
		List<String> documentIds = new ArrayList<>();
		for (CloudFile file : files) {
			VaultPath path = requireNonNull(vaultDocumentRepository.pathOf(file), "Not inside a vault");
			documentIds.add(vaultDocumentRepository.open(path, progressAware));
		}
		return documentIds;
	}
}
