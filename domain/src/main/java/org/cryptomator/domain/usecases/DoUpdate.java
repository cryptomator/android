package org.cryptomator.domain.usecases;

import java.io.File;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
public class DoUpdate {

	private final UpdateCheckRepository updateCheckRepository;
	private final File file;

	DoUpdate(final UpdateCheckRepository updateCheckRepository, @Parameter File file) {
		this.updateCheckRepository = updateCheckRepository;
		this.file = file;
	}

	public void execute() throws BackendException {
		updateCheckRepository.update(file);
	}
}
