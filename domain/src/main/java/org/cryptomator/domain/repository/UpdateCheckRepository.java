package org.cryptomator.domain.repository;

import java.io.File;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.update.GeneralUpdateErrorException;
import org.cryptomator.domain.usecases.UpdateCheck;
import org.cryptomator.util.Optional;

public interface UpdateCheckRepository {

	Optional<UpdateCheck> getUpdateCheck(String version) throws BackendException;

	String getLicense();

	void setLicense(String license);

	void update(File file) throws GeneralUpdateErrorException;
}
