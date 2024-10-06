package org.cryptomator.domain.usecases.cloud;

import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import java.util.List;

@UseCase
public class AssociateThumbnails {

	private final CloudContentRepository cloudContentRepository;
	private final List<CloudNode> list;

	public AssociateThumbnails(CloudContentRepository cloudContentRepository, //
			@Parameter List<CloudNode> list) {
		this.cloudContentRepository = cloudContentRepository;
		this.list = list;
	}

	public Integer execute(ProgressAware<FileTransferState> progressAware) throws BackendException {
		return cloudContentRepository.associateThumbnails(list, progressAware);
	}
}
