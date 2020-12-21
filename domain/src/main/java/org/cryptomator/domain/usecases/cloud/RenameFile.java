package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ResultRenamed;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
class RenameFile {

	private final CloudContentRepository cloudContentRepository;
	private final CloudFile file;
	private final String newName;

	public RenameFile(CloudContentRepository cloudContentRepository, @Parameter CloudFile file, @Parameter String newName) {
		this.cloudContentRepository = cloudContentRepository;
		this.file = file;
		this.newName = newName;

	}

	public ResultRenamed<CloudFile> execute() throws BackendException {
		CloudFile targetFile = cloudContentRepository.file(file.getParent(), newName);
		CloudFile movedFile = cloudContentRepository.move(file, targetFile);
		return new ResultRenamed<>(movedFile, file.getName());
	}

}
