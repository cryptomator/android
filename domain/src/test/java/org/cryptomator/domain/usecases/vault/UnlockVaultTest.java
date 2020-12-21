package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

public class UnlockVaultTest {

	private static final String A_STRING = "89dfhsjdhfjsd";

	private UnlockToken unlockToken;

	private Vault vault;

	private CloudRepository cloudRepository;

	private UnlockVault inTest;

	@BeforeEach
	public void setup() {
		unlockToken = Mockito.mock(UnlockToken.class);
		vault = Mockito.mock(Vault.class);
		cloudRepository = Mockito.mock(CloudRepository.class);
		inTest = Mockito.mock(UnlockVault.class);
	}

	@Test
	public void testExecuteDelegatesToUnlockWhenInvokedWithVault() throws BackendException {
		inTest = new UnlockVault(cloudRepository, VaultOrUnlockToken.from(vault), A_STRING);
		inTest.execute();

		verify(cloudRepository).unlock(vault, A_STRING);
	}

	@Test
	public void testExecuteDelegatesToUnlockWhenInvokedWithUnlockToken() throws BackendException {
		inTest = new UnlockVault(cloudRepository, VaultOrUnlockToken.from(unlockToken), A_STRING);
		inTest.execute();

		verify(cloudRepository).unlock(unlockToken, A_STRING);
	}

}
