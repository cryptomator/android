package org.cryptomator.domain.usecases.vault;

import com.google.common.base.Optional;

import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NoSuchCloudFileException;
import org.cryptomator.domain.exception.NoSuchVaultException;
import org.cryptomator.domain.repository.CloudRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.when;

public class GetRecoveryKeyTest {

	private static final String A_PASSWORD = "89dfhsjdhfjsd";

	private static final String A_RECOVERY_KEY = "pathway lift abuse";

	private Vault vault;

	private CloudRepository cloudRepository;

	private Optional<UnverifiedVaultConfig> unverifiedVaultConfig;

	private GetRecoveryKey inTest;

	@BeforeEach
	public void setup() {
		vault = Mockito.mock(Vault.class);
		cloudRepository = Mockito.mock(CloudRepository.class);
		unverifiedVaultConfig = Optional.absent();
		inTest = new GetRecoveryKey(cloudRepository, vault, unverifiedVaultConfig, A_PASSWORD);
	}

	@Test
	public void testExecuteReturnsTheRecoveryKeyOfTheVault() throws BackendException {
		when(cloudRepository.recoveryKey(vault, unverifiedVaultConfig, A_PASSWORD)).thenReturn(A_RECOVERY_KEY);

		Assertions.assertEquals(A_RECOVERY_KEY, inTest.execute());
	}

	@Test
	public void testExecuteReportsAMissingMasterkeyFileAsMissingVault() throws BackendException {
		when(cloudRepository.recoveryKey(vault, unverifiedVaultConfig, A_PASSWORD)).thenThrow(new NoSuchCloudFileException("masterkey.cryptomator"));

		NoSuchVaultException thrown = Assertions.assertThrows(NoSuchVaultException.class, () -> inTest.execute());

		Assertions.assertSame(vault, thrown.getVault());
	}

	@Test
	public void testExecutePassesOtherFailuresOn() throws BackendException {
		FatalBackendException failure = new FatalBackendException("failed");
		when(cloudRepository.recoveryKey(vault, unverifiedVaultConfig, A_PASSWORD)).thenThrow(failure);

		Assertions.assertSame(failure, Assertions.assertThrows(FatalBackendException.class, () -> inTest.execute()));
	}
}
