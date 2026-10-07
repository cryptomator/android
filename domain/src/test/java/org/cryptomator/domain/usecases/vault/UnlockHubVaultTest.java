package org.cryptomator.domain.usecases.vault;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.UnverifiedHubVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.hub.HubInvalidVersionException;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.HubRepository;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UnlockHubVaultTest {

	private static final String ACCESS_TOKEN = "hub-access-token";
	private static final String VAULT_KEY_JWE = "vault-key-jwe";
	private static final String USER_PRIVATE_KEY = "user-private-key";

	private CloudRepository cloudRepository;
	private HubRepository hubRepository;
	private UnverifiedHubVaultConfig unverifiedVaultConfig;
	private Cloud cloud;
	private Vault vault;

	@BeforeEach
	public void setup() {
		cloudRepository = mock(CloudRepository.class);
		hubRepository = mock(HubRepository.class);
		unverifiedVaultConfig = mock(UnverifiedHubVaultConfig.class);
		cloud = mock(Cloud.class);
		when(cloud.type()).thenReturn(CloudType.WEBDAV);
		vault = Vault.aVault() //
				.withId(1L) //
				.withName("TestVault") //
				.withPath("/vaults/test") //
				.withCloud(cloud) //
				.withPosition(0) //
				.build();
	}

	@Test
	@DisplayName("API level below minimum throws HubInvalidVersionException")
	public void testApiLevelBelowMinimumThrowsException() throws BackendException {
		when(hubRepository.getConfig(unverifiedVaultConfig, ACCESS_TOKEN)) //
				.thenReturn(new HubRepository.ConfigDto(0));

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);

		assertThrows(HubInvalidVersionException.class, inTest::execute);
	}

	@Test
	@DisplayName("Negative API level throws HubInvalidVersionException")
	public void testNegativeApiLevelThrowsException() throws BackendException {
		when(hubRepository.getConfig(unverifiedVaultConfig, ACCESS_TOKEN)) //
				.thenReturn(new HubRepository.ConfigDto(-1));

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);

		assertThrows(HubInvalidVersionException.class, inTest::execute);
	}

	@Test
	@DisplayName("API level at minimum proceeds without exception")
	public void testApiLevelAtMinimumProceeds() throws BackendException {
		setupHubResponses(1, HubRepository.SubscriptionState.ACTIVE);
		Cloud expectedCloud = mock(Cloud.class);
		when(cloudRepository.unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class))) //
				.thenReturn(expectedCloud);

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);
		Cloud result = inTest.execute();

		assertThat(result, is(expectedCloud));
	}

	@Test
	@DisplayName("API level above minimum proceeds without exception")
	public void testApiLevelAboveMinimumProceeds() throws BackendException {
		setupHubResponses(2, HubRepository.SubscriptionState.ACTIVE);
		Cloud expectedCloud = mock(Cloud.class);
		when(cloudRepository.unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class))) //
				.thenReturn(expectedCloud);

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);
		Cloud result = inTest.execute();

		assertThat(result, is(expectedCloud));
	}

	@Test
	@DisplayName("ACTIVE subscription sets hubPaidLicense to true")
	public void testActiveSubscriptionSetsHubPaidLicenseTrue() throws BackendException {
		setupHubResponses(1, HubRepository.SubscriptionState.ACTIVE);
		when(cloudRepository.unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class))) //
				.thenReturn(mock(Cloud.class));

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);
		inTest.execute();

		ArgumentCaptor<Vault> vaultCaptor = ArgumentCaptor.forClass(Vault.class);
		verify(cloudRepository).unlock(vaultCaptor.capture(), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class));
		assertThat(vaultCaptor.getValue().hasHubPaidLicense(), is(true));
	}

	@Test
	@DisplayName("INACTIVE subscription sets hubPaidLicense to false")
	public void testInactiveSubscriptionSetsHubPaidLicenseFalse() throws BackendException {
		setupHubResponses(1, HubRepository.SubscriptionState.INACTIVE);
		when(cloudRepository.unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class))) //
				.thenReturn(mock(Cloud.class));

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);
		inTest.execute();

		ArgumentCaptor<Vault> vaultCaptor = ArgumentCaptor.forClass(Vault.class);
		verify(cloudRepository).unlock(vaultCaptor.capture(), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class));
		assertThat(vaultCaptor.getValue().hasHubPaidLicense(), is(false));
	}

	@Test
	@DisplayName("Vault passed to unlock has isHubVault set to true")
	public void testVaultPassedToUnlockHasHubVaultTrue() throws BackendException {
		setupHubResponses(1, HubRepository.SubscriptionState.ACTIVE);
		when(cloudRepository.unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class))) //
				.thenReturn(mock(Cloud.class));

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);
		inTest.execute();

		ArgumentCaptor<Vault> vaultCaptor = ArgumentCaptor.forClass(Vault.class);
		verify(cloudRepository).unlock(vaultCaptor.capture(), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class));
		assertThat(vaultCaptor.getValue().isHubVault(), is(true));
	}

	@Test
	@DisplayName("Unlock delegates to cloudRepository with correct parameters")
	public void testUnlockDelegatesToCloudRepositoryWithCorrectParameters() throws BackendException {
		setupHubResponses(1, HubRepository.SubscriptionState.ACTIVE);
		Cloud expectedCloud = mock(Cloud.class);
		when(cloudRepository.unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class))) //
				.thenReturn(expectedCloud);

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);
		Cloud result = inTest.execute();

		assertThat(result, is(expectedCloud));
		verify(cloudRepository).unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class));
	}

	@Test
	@DisplayName("onCancel propagates to the cancelled flag passed to unlock")
	public void testOnCancelPropagatesFlag() throws BackendException {
		setupHubResponses(1, HubRepository.SubscriptionState.ACTIVE);
		ArgumentCaptor<Flag> flagCaptor = ArgumentCaptor.forClass(Flag.class);
		when(cloudRepository.unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), flagCaptor.capture())) //
				.thenReturn(mock(Cloud.class));

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);
		inTest.execute();

		Flag cancelledFlag = flagCaptor.getValue();
		assertThat(cancelledFlag.get(), is(false));

		inTest.onCancel();
		assertThat(cancelledFlag.get(), is(true));
	}

	@Test
	@DisplayName("Vault passed to unlock preserves original vault properties")
	public void testVaultPassedToUnlockPreservesOriginalProperties() throws BackendException {
		setupHubResponses(1, HubRepository.SubscriptionState.ACTIVE);
		when(cloudRepository.unlock(Mockito.any(Vault.class), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class))) //
				.thenReturn(mock(Cloud.class));

		UnlockHubVault inTest = new UnlockHubVault(cloudRepository, hubRepository, vault, unverifiedVaultConfig, ACCESS_TOKEN);
		inTest.execute();

		ArgumentCaptor<Vault> vaultCaptor = ArgumentCaptor.forClass(Vault.class);
		verify(cloudRepository).unlock(vaultCaptor.capture(), Mockito.eq(unverifiedVaultConfig), Mockito.eq(VAULT_KEY_JWE), Mockito.eq(USER_PRIVATE_KEY), Mockito.any(Flag.class));
		Vault capturedVault = vaultCaptor.getValue();
		assertThat(capturedVault.getName(), is("TestVault"));
		assertThat(capturedVault.getPath(), is("/vaults/test"));
		assertThat(capturedVault.getCloud(), is(cloud));
	}

	private void setupHubResponses(int apiLevel, HubRepository.SubscriptionState subscriptionState) throws BackendException {
		when(hubRepository.getConfig(unverifiedVaultConfig, ACCESS_TOKEN)) //
				.thenReturn(new HubRepository.ConfigDto(apiLevel));
		when(hubRepository.getVaultAccess(unverifiedVaultConfig, ACCESS_TOKEN)) //
				.thenReturn(new HubRepository.VaultAccess(VAULT_KEY_JWE, subscriptionState));
		when(hubRepository.getDevice(unverifiedVaultConfig, ACCESS_TOKEN)) //
				.thenReturn(new HubRepository.DeviceDto(USER_PRIVATE_KEY));
	}

}
