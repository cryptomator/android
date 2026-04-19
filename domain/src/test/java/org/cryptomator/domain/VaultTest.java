package org.cryptomator.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VaultTest {

	private Cloud cloud;

	@BeforeEach
	public void setup() {
		cloud = mock(Cloud.class);
		when(cloud.type()).thenReturn(CloudType.WEBDAV);
	}

	private Vault.Builder minimalVaultBuilder() {
		return Vault.aVault()
				.withId(1L)
				.withName("TestVault")
				.withPath("/vaults/test")
				.withCloud(cloud)
				.withPosition(0);
	}

	@Nested
	@DisplayName("hubVault field")
	class HubVaultField {

		@Test
		@DisplayName("Default hubVault is false when not set")
		public void defaultHubVaultIsFalse() {
			Vault vault = minimalVaultBuilder().build();

			assertFalse(vault.isHubVault());
		}

		@Test
		@DisplayName("withHubVault(true) sets isHubVault() to true")
		public void withHubVaultTrueSetsHubVaultTrue() {
			Vault vault = minimalVaultBuilder()
					.withHubVault(true)
					.build();

			assertTrue(vault.isHubVault());
		}

		@Test
		@DisplayName("withHubVault(false) sets isHubVault() to false")
		public void withHubVaultFalseSetsHubVaultFalse() {
			Vault vault = minimalVaultBuilder()
					.withHubVault(false)
					.build();

			assertFalse(vault.isHubVault());
		}

		@Test
		@DisplayName("withHubVault returns the builder for chaining")
		public void withHubVaultReturnsBuilderForChaining() {
			Vault.Builder builder = minimalVaultBuilder();
			Vault.Builder returned = builder.withHubVault(true);

			assertThat(returned, is(builder));
		}
	}

	@Nested
	@DisplayName("hubPaidLicense field")
	class HubPaidLicenseField {

		@Test
		@DisplayName("Default hubPaidLicense is false when not set")
		public void defaultHubPaidLicenseIsFalse() {
			Vault vault = minimalVaultBuilder().build();

			assertFalse(vault.hasHubPaidLicense());
		}

		@Test
		@DisplayName("withHubPaidLicense(true) sets hasHubPaidLicense() to true")
		public void withHubPaidLicenseTrueSetsHubPaidLicenseTrue() {
			Vault vault = minimalVaultBuilder()
					.withHubPaidLicense(true)
					.build();

			assertTrue(vault.hasHubPaidLicense());
		}

		@Test
		@DisplayName("withHubPaidLicense(false) sets hasHubPaidLicense() to false")
		public void withHubPaidLicenseFalseSetsHubPaidLicenseFalse() {
			Vault vault = minimalVaultBuilder()
					.withHubPaidLicense(false)
					.build();

			assertFalse(vault.hasHubPaidLicense());
		}

		@Test
		@DisplayName("withHubPaidLicense returns the builder for chaining")
		public void withHubPaidLicenseReturnsBuilderForChaining() {
			Vault.Builder builder = minimalVaultBuilder();
			Vault.Builder returned = builder.withHubPaidLicense(true);

			assertThat(returned, is(builder));
		}
	}

	@Nested
	@DisplayName("Hub vault combined state")
	class HubVaultCombinedState {

		@Test
		@DisplayName("Hub vault with active paid license has both flags true")
		public void hubVaultWithActivePaidLicense() {
			Vault vault = minimalVaultBuilder()
					.withHubVault(true)
					.withHubPaidLicense(true)
					.build();

			assertTrue(vault.isHubVault());
			assertTrue(vault.hasHubPaidLicense());
		}

		@Test
		@DisplayName("Hub vault with inactive subscription has hubVault true and hubPaidLicense false")
		public void hubVaultWithInactiveSubscription() {
			Vault vault = minimalVaultBuilder()
					.withHubVault(true)
					.withHubPaidLicense(false)
					.build();

			assertTrue(vault.isHubVault());
			assertFalse(vault.hasHubPaidLicense());
		}

		@Test
		@DisplayName("Non-hub vault always has hubPaidLicense false")
		public void nonHubVaultHasHubPaidLicenseFalse() {
			Vault vault = minimalVaultBuilder()
					.withHubVault(false)
					.withHubPaidLicense(false)
					.build();

			assertFalse(vault.isHubVault());
			assertFalse(vault.hasHubPaidLicense());
		}
	}

	@Nested
	@DisplayName("aCopyOf preserves hub fields")
	class ACopyOfPreservesHubFields {

		@Test
		@DisplayName("aCopyOf preserves hubVault=true")
		public void aCopyOfPreservesHubVaultTrue() {
			Vault original = minimalVaultBuilder()
					.withHubVault(true)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertTrue(copy.isHubVault());
		}

		@Test
		@DisplayName("aCopyOf preserves hubVault=false")
		public void aCopyOfPreservesHubVaultFalse() {
			Vault original = minimalVaultBuilder()
					.withHubVault(false)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertFalse(copy.isHubVault());
		}

		@Test
		@DisplayName("aCopyOf preserves hubPaidLicense=true")
		public void aCopyOfPreservesHubPaidLicenseTrue() {
			Vault original = minimalVaultBuilder()
					.withHubPaidLicense(true)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertTrue(copy.hasHubPaidLicense());
		}

		@Test
		@DisplayName("aCopyOf preserves hubPaidLicense=false")
		public void aCopyOfPreservesHubPaidLicenseFalse() {
			Vault original = minimalVaultBuilder()
					.withHubPaidLicense(false)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertFalse(copy.hasHubPaidLicense());
		}

		@Test
		@DisplayName("aCopyOf then withHubVault overrides the copied value")
		public void aCopyOfThenWithHubVaultOverridesValue() {
			Vault original = minimalVaultBuilder()
					.withHubVault(false)
					.build();

			Vault copy = Vault.aCopyOf(original)
					.withHubVault(true)
					.build();

			assertTrue(copy.isHubVault());
		}

		@Test
		@DisplayName("aCopyOf then withHubPaidLicense overrides the copied value")
		public void aCopyOfThenWithHubPaidLicenseOverridesValue() {
			Vault original = minimalVaultBuilder()
					.withHubPaidLicense(false)
					.build();

			Vault copy = Vault.aCopyOf(original)
					.withHubPaidLicense(true)
					.build();

			assertTrue(copy.hasHubPaidLicense());
		}

		@Test
		@DisplayName("aCopyOf preserves both hub flags together")
		public void aCopyOfPreservesBothHubFlagsTogether() {
			Vault original = minimalVaultBuilder()
					.withHubVault(true)
					.withHubPaidLicense(true)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertTrue(copy.isHubVault());
			assertTrue(copy.hasHubPaidLicense());
		}

		@Test
		@DisplayName("aCopyOf preserves other original properties alongside hub fields")
		public void aCopyOfPreservesOtherPropertiesAlongsideHubFields() {
			Vault original = Vault.aVault()
					.withId(42L)
					.withName("MyHub")
					.withPath("/hub/path")
					.withCloud(cloud)
					.withPosition(3)
					.withHubVault(true)
					.withHubPaidLicense(true)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertThat(copy.getId(), is(42L));
			assertThat(copy.getName(), is("MyHub"));
			assertThat(copy.getPath(), is("/hub/path"));
			assertThat(copy.getPosition(), is(3));
			assertTrue(copy.isHubVault());
			assertTrue(copy.hasHubPaidLicense());
		}
	}
}