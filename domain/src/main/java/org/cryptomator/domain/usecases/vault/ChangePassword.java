package org.cryptomator.domain.usecases.vault;

import org.cryptomator.cryptolib.api.InvalidPassphraseException;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.NoSuchVaultException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.generator.Parameter;
import org.cryptomator.generator.UseCase;

import static org.cryptomator.util.ExceptionUtil.contains;

@UseCase
class ChangePassword {

	private final CloudRepository cloudRepository;
	private final Vault vault;
	private final String oldPassword;
	private final String newPassword;

	public ChangePassword(CloudRepository cloudRepository, //
			@Parameter Vault vault, //
			@Parameter String oldPassword, //
			@Parameter String newPassword) {
		this.cloudRepository = cloudRepository;
		this.vault = vault;
		this.oldPassword = oldPassword;
		this.newPassword = newPassword;
	}

	public void execute() throws BackendException {
		try {
			if (cloudRepository.isVaultPasswordValid(vault, oldPassword)) {
				cloudRepository.changePassword(vault, oldPassword, newPassword);
			} else {
				throw new InvalidPassphraseException();
			}
		} catch (BackendException e) {
			if (contains(e, NoSuchCloudFileException.class)) {
				throw new NoSuchVaultException(vault, e);
			}
			throw e;
		}
	}
}
