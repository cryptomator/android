package org.cryptomator.domain.usecases.vault;

import com.google.common.base.Optional;

import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

public class UnlockVaultUsingMasterkeyTest {

	private static final String A_STRING = "89dfhsjdhfjsd";

	private UnlockToken unlockToken;

	private Vault vault;

	private CloudRepository cloudRepository;

	private UnlockVaultUsingMasterkey inTest;

	private Optional<UnverifiedVaultConfig> unverifiedVaultConfig;

	@BeforeEach
	public void setup() {
		unlockToken = Mockito.mock(UnlockToken.class);
		vault = Mockito.mock(Vault.class);
		cloudRepository = Mockito.mock(CloudRepository.class);
		unverifiedVaultConfig = Mockito.mock(Optional.class);
		inTest = Mockito.mock(UnlockVaultUsingMasterkey.class);
	}

	@Test
	public void testExecuteDelegatesToUnlockWhenInvokedWithVault() throws BackendException {
		inTest = new UnlockVaultUsingMasterkey(cloudRepository, VaultOrUnlockToken.from(vault), unverifiedVaultConfig, A_STRING);
		inTest.execute();

		verify(cloudRepository).unlock(Mockito.eq(vault), Mockito.any(), Mockito.eq(A_STRING), Mockito.any());
	}

	@Test
	public void testExecuteDelegatesToUnlockWhenInvokedWithUnlockToken() throws BackendException {
		inTest = new UnlockVaultUsingMasterkey(cloudRepository, VaultOrUnlockToken.from(unlockToken), unverifiedVaultConfig, A_STRING);
		inTest.execute();

		verify(cloudRepository).unlock(Mockito.eq(unlockToken), Mockito.any(), Mockito.eq(A_STRING), Mockito.any());
	}

}
