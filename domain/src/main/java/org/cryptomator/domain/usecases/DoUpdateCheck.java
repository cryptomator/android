package org.cryptomator.domain.usecases;

import com.google.common.base.Optional;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

@UseCase
public class DoUpdateCheck {

	private final String version;
	private final UpdateCheckRepository updateCheckRepository;

	DoUpdateCheck(final UpdateCheckRepository updateCheckRepository, @Parameter String version) {
		this.updateCheckRepository = updateCheckRepository;
		this.version = version;
	}

	public Optional<UpdateCheck> execute() throws BackendException {
		return updateCheckRepository.getUpdateCheck(version);
	}
}
