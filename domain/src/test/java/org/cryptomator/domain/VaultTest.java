package org.cryptomator.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VaultTest {

	private static Vault buildMinimalVault() {
		Cloud cloud = mock(Cloud.class);
		when(cloud.type()).thenReturn(CloudType.WEBDAV);
		return Vault.aVault()
				.withId(1L)
				.withName("Test")
				.withPath("/test")
				.withCloud(cloud)
				.withPosition(0)
				.build();
	}

	private static Vault.Builder minimalVaultBuilder() {
		Cloud cloud = mock(Cloud.class);
		when(cloud.type()).thenReturn(CloudType.WEBDAV);
		return Vault.aVault()
				.withId(1L)
				.withName("Test")
				.withPath("/test")
				.withCloud(cloud)
				.withPosition(0);
	}

	@Nested
	@DisplayName("Hub fields - default values")
	class DefaultValues {

		@Test
		@DisplayName("isHubVault defaults to false when not set")
		void isHubVaultDefaultsFalse() {
			Vault vault = buildMinimalVault();

			assertThat(vault.isHubVault(), is(false));
		}

		@Test
		@DisplayName("hasHubPaidLicense defaults to false when not set")
		void hasHubPaidLicenseDefaultsFalse() {
			Vault vault = buildMinimalVault();

			assertThat(vault.hasHubPaidLicense(), is(false));
		}
	}

	@Nested
	@DisplayName("Hub fields - builder setter")
	class BuilderSetters {

		@Test
		@DisplayName("withHubVault(true) sets isHubVault to true")
		void withHubVaultTrue() {
			Vault vault = minimalVaultBuilder()
					.withHubVault(true)
					.build();

			assertThat(vault.isHubVault(), is(true));
		}

		@Test
		@DisplayName("withHubVault(false) keeps isHubVault false")
		void withHubVaultFalse() {
			Vault vault = minimalVaultBuilder()
					.withHubVault(false)
					.build();

			assertThat(vault.isHubVault(), is(false));
		}

		@Test
		@DisplayName("withHubPaidLicense(true) sets hasHubPaidLicense to true")
		void withHubPaidLicenseTrue() {
			Vault vault = minimalVaultBuilder()
					.withHubPaidLicense(true)
					.build();

			assertThat(vault.hasHubPaidLicense(), is(true));
		}

		@Test
		@DisplayName("withHubPaidLicense(false) keeps hasHubPaidLicense false")
		void withHubPaidLicenseFalse() {
			Vault vault = minimalVaultBuilder()
					.withHubPaidLicense(false)
					.build();

			assertThat(vault.hasHubPaidLicense(), is(false));
		}

		@Test
		@DisplayName("withHubVault and withHubPaidLicense can both be set independently")
		void hubVaultAndPaidLicenseAreIndependent() {
			Vault vaultHubOnly = minimalVaultBuilder()
					.withHubVault(true)
					.withHubPaidLicense(false)
					.build();
			Vault vaultPaidOnly = minimalVaultBuilder()
					.withHubVault(false)
					.withHubPaidLicense(true)
					.build();
			Vault vaultBoth = minimalVaultBuilder()
					.withHubVault(true)
					.withHubPaidLicense(true)
					.build();

			assertThat(vaultHubOnly.isHubVault(), is(true));
			assertThat(vaultHubOnly.hasHubPaidLicense(), is(false));

			assertThat(vaultPaidOnly.isHubVault(), is(false));
			assertThat(vaultPaidOnly.hasHubPaidLicense(), is(true));

			assertThat(vaultBoth.isHubVault(), is(true));
			assertThat(vaultBoth.hasHubPaidLicense(), is(true));
		}
	}

	@Nested
	@DisplayName("aCopyOf - hub field propagation")
	class CopyOf {

		@Test
		@DisplayName("aCopyOf preserves isHubVault=false")
		void copyPreservesHubVaultFalse() {
			Vault original = buildMinimalVault();

			Vault copy = Vault.aCopyOf(original).build();

			assertThat(copy.isHubVault(), is(false));
		}

		@Test
		@DisplayName("aCopyOf preserves isHubVault=true")
		void copyPreservesHubVaultTrue() {
			Vault original = minimalVaultBuilder()
					.withHubVault(true)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertThat(copy.isHubVault(), is(true));
		}

		@Test
		@DisplayName("aCopyOf preserves hasHubPaidLicense=false")
		void copyPreservesHubPaidLicenseFalse() {
			Vault original = buildMinimalVault();

			Vault copy = Vault.aCopyOf(original).build();

			assertThat(copy.hasHubPaidLicense(), is(false));
		}

		@Test
		@DisplayName("aCopyOf preserves hasHubPaidLicense=true")
		void copyPreservesHubPaidLicenseTrue() {
			Vault original = minimalVaultBuilder()
					.withHubPaidLicense(true)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertThat(copy.hasHubPaidLicense(), is(true));
		}

		@Test
		@DisplayName("aCopyOf allows overriding isHubVault after copy")
		void copyAllowsOverridingHubVault() {
			Vault original = minimalVaultBuilder()
					.withHubVault(false)
					.build();

			Vault modified = Vault.aCopyOf(original)
					.withHubVault(true)
					.build();

			assertThat(modified.isHubVault(), is(true));
		}

		@Test
		@DisplayName("aCopyOf allows overriding hasHubPaidLicense after copy")
		void copyAllowsOverridingHubPaidLicense() {
			Vault original = minimalVaultBuilder()
					.withHubPaidLicense(false)
					.build();

			Vault modified = Vault.aCopyOf(original)
					.withHubPaidLicense(true)
					.build();

			assertThat(modified.hasHubPaidLicense(), is(true));
		}

		@Test
		@DisplayName("aCopyOf with both hub fields set true preserves both in copy")
		void copyPreservesBothHubFieldsTrue() {
			Vault original = minimalVaultBuilder()
					.withHubVault(true)
					.withHubPaidLicense(true)
					.build();

			Vault copy = Vault.aCopyOf(original).build();

			assertThat(copy.isHubVault(), is(true));
			assertThat(copy.hasHubPaidLicense(), is(true));
		}

		@Test
		@DisplayName("aCopyOf does not mutate the original vault")
		void copyDoesNotMutateOriginal() {
			Vault original = minimalVaultBuilder()
					.withHubVault(false)
					.withHubPaidLicense(false)
					.build();

			Vault.aCopyOf(original)
					.withHubVault(true)
					.withHubPaidLicense(true)
					.build();

			assertThat(original.isHubVault(), is(false));
			assertThat(original.hasHubPaidLicense(), is(false));
		}
	}

	@Nested
	@DisplayName("Hub fields - regression: non-hub vaults remain unaffected")
	class NonHubVaultRegression {

		@Test
		@DisplayName("Standard vault without hub flags has false for both")
		void standardVaultHasFalseForBothHubFlags() {
			Cloud cloud = mock(Cloud.class);
			when(cloud.type()).thenReturn(CloudType.S3);
			Vault vault = Vault.aVault()
					.withId(42L)
					.withName("MyVault")
					.withPath("/s3/my-vault")
					.withCloud(cloud)
					.withPosition(3)
					.build();

			assertThat(vault.isHubVault(), is(false));
			assertThat(vault.hasHubPaidLicense(), is(false));
		}
	}
}