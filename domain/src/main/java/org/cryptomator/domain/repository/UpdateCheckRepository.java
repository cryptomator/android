package org.cryptomator.domain.repository;

import com.google.common.base.Optional;

import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.update.GeneralUpdateErrorException;
import org.cryptomator.domain.usecases.UpdateCheck;

import java.io.File;

public interface UpdateCheckRepository {

	/**
 * Retrieves the update-check information for the specified application version.
 *
 * @param version the application version identifier to query (e.g., semantic version string)
 * @return an {@code Optional<UpdateCheck>} containing the update information for the given version, or {@code Optional.absent()} if no data is available
 * @throws BackendException if a backend error prevents retrieving the update information
 */
Optional<UpdateCheck> getUpdateCheck(String version) throws BackendException;

	/**
 * Applies an update using the given update file.
 *
 * @param file the update package file to apply
 * @throws GeneralUpdateErrorException if the update cannot be applied (for example due to I/O errors,
 *                                     validation failures, or installation problems)
 */
void update(File file) throws GeneralUpdateErrorException;
}
