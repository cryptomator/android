package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileNameCryptor;
import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException;
import org.cryptomator.data.util.CopyStream;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudType;
import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.domain.usecases.vault.UnlockToken;
import org.cryptomator.util.Optional;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.stream.Collectors;

import static org.cryptomator.cryptolib.api.Masterkey.SUBKEY_LEN_BYTES;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.DATA_DIR_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.DEFAULT_MAX_FILE_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_BACKUP_FILE_EXT;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_FILE_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_SCHEME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MAX_VAULT_VERSION;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.ROOT_DIR_ID;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.VAULT_FILE_NAME;
import static org.cryptomator.data.cloud.crypto.VaultCipherCombo.SIV_CTRMAC;
import static org.cryptomator.domain.usecases.ProgressAware.NO_OP_PROGRESS_AWARE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MasterkeyCryptoCloudProviderTest {

	private final String masterkeyV8 = "{  \"version\": 999,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"hmacMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"versionMac\": \"trDKXqDhu94/VPuoWaQGBm8hwSPYc0D9t6DRRxKZ65k=\"}";
	private final String masterkeyV7 = "{  \"version\": 7,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"hmacMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"versionMac\": \"cn2sAK6l9p1/w9deJVUuW3h7br056mpv5srvALiYw+g=\"}";
	private final String vaultConfig = "eyJraWQiOiJtYXN0ZXJrZXlmaWxlOm1hc3RlcmtleS5jcnlwdG9tYXRvciIsImFsZyI6IkhTNTEyIn0.eyJtYXhGaWxlbmFtZUxlbiI6MjIwLCJmb3JtYXQiOjgsImNpcGhlckNvbWJvIjoiU0lWX0NUUk1BQyJ9.umiAcGObWuVISugrQu16hznDHIFM7moD1ukA1r5V1DRA0GjHQk1p6S9hkL0PaMD7xl04jSttMRalOYU1sg4wqQ";

	private Context context;
	private Cloud cloud;
	private CloudContentRepository cloudContentRepository;
	private CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory;
	private Vault vault;
	private VaultConfig.VaultConfigBuilder vaultConfigBuilder;
	private Cryptor cryptor;
	private FileNameCryptor fileNameCryptor;
	private SecureRandom secureRandom;
	private MasterkeyCryptoCloudProvider inTest;

	@BeforeEach
	public void setUp() {
		context = Mockito.mock(Context.class);
		cloud = Mockito.mock(Cloud.class);
		cloudContentRepository = Mockito.mock(CloudContentRepository.class);

		cryptoCloudContentRepositoryFactory = Mockito.mock(CryptoCloudContentRepositoryFactory.class);

		vault = Mockito.mock(Vault.class);
		vaultConfigBuilder = VaultConfig.createVaultConfig().id("");

		cryptor = Mockito.mock(Cryptor.class);
		fileNameCryptor = Mockito.mock(FileNameCryptor.class);
		secureRandom = Mockito.mock(SecureRandom.class);

		Mockito.when(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor);

		byte[] key = new byte[SUBKEY_LEN_BYTES + SUBKEY_LEN_BYTES];
		Mockito.doNothing().when(secureRandom).nextBytes(key);

		inTest = Mockito.spy(new MasterkeyCryptoCloudProvider(cloudContentRepository, cryptoCloudContentRepositoryFactory, secureRandom));
	}

	@Test
	@DisplayName("create(\"/foo\", \"foo\")")
	public void testCreateVault() throws BackendException {
		TestFolder rootFolder = new RootTestFolder(cloud);
		TestFolder foo = new TestFolder(rootFolder, "foo", "/foo");
		TestFile vaultFile = new TestFile(foo, VAULT_FILE_NAME, "/foo/" + VAULT_FILE_NAME, Optional.empty(), Optional.empty());
		TestFile masterKeyFile = new TestFile(foo, MASTERKEY_FILE_NAME, "/foo/" + MASTERKEY_FILE_NAME, Optional.empty(), Optional.empty());

		Mockito.when(cloudContentRepository.file(foo, VAULT_FILE_NAME)).thenReturn(vaultFile);
		Mockito.when(cloudContentRepository.file(foo, MASTERKEY_FILE_NAME)).thenReturn(masterKeyFile);

		// 1. write masterkey
		Mockito.when(cloudContentRepository.write(Mockito.eq(masterKeyFile), Mockito.any(DataSource.class), Mockito.eq(NO_OP_PROGRESS_AWARE), Mockito.eq(false), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String masterKeyFileContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).lines().collect(Collectors.joining());
			assertThat(masterKeyFileContent, is(masterkeyV8));
			return invocationOnMock.getArgument(0);
		});

		// 2. initialize vault
		Mockito.when(cloudContentRepository.write(Mockito.eq(vaultFile), Mockito.any(DataSource.class), Mockito.eq(NO_OP_PROGRESS_AWARE), Mockito.eq(false), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String vaultConfigFileContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).lines().collect(Collectors.joining());
			assertThat(vaultConfigFileContent, is(vaultConfig));
			return invocationOnMock.getArgument(0);
		});

		// 3. create root folder
		String rootDirHash = "KG6TFDGKXGZEGWRZOGTDFDF4YEGAZO6Q";

		TestFolder dFolder = new TestFolder(foo, "d", "/foo/" + DATA_DIR_NAME);
		TestFolder lvl1Dir = new TestFolder(dFolder, rootDirHash.substring(0, 2), "/foo/" + DATA_DIR_NAME + "/" + rootDirHash.substring(0, 2));
		TestFolder lvl2Dir = new TestFolder(lvl1Dir, rootDirHash.substring(2), "/foo/" + DATA_DIR_NAME + "/" + rootDirHash.substring(0, 2) + "/" + rootDirHash.substring(2));


		Mockito.when(cloudContentRepository.folder(foo, DATA_DIR_NAME)).thenReturn(dFolder);
		Mockito.when(cloudContentRepository.create(dFolder)).thenReturn(dFolder);

		Mockito.when(cryptor.fileNameCryptor().hashDirectoryId(ROOT_DIR_ID)).thenReturn(ROOT_DIR_ID);

		Mockito.when(cloudContentRepository.folder(dFolder, lvl1Dir.getName())).thenReturn(lvl1Dir);
		Mockito.when(cloudContentRepository.create(lvl1Dir)).thenReturn(lvl1Dir);

		Mockito.when(cloudContentRepository.folder(lvl1Dir, lvl2Dir.getName())).thenReturn(lvl2Dir);
		Mockito.when(cloudContentRepository.create(lvl2Dir)).thenReturn(lvl2Dir);

		inTest.create(foo, "foo", vaultConfigBuilder);

		Mockito.verify(cloudContentRepository).write(Mockito.eq(masterKeyFile), Mockito.any(DataSource.class), Mockito.eq(NO_OP_PROGRESS_AWARE), Mockito.eq(false), Mockito.anyLong());
		Mockito.verify(cloudContentRepository).write(Mockito.eq(vaultFile), Mockito.any(DataSource.class), Mockito.eq(NO_OP_PROGRESS_AWARE), Mockito.eq(false), Mockito.anyLong());
		Mockito.verify(cloudContentRepository).create(dFolder);
		Mockito.verify(cloudContentRepository).create(lvl1Dir);
		Mockito.verify(cloudContentRepository).create(lvl2Dir);
	}

	@Test
	@DisplayName("lock(\"foo\")")
	public void testLockVault() {
		inTest.lock(vault);
		Mockito.verify(cryptoCloudContentRepositoryFactory).deregisterCryptor(vault);
	}

	@Test
	@DisplayName("unlock(\"foo\")")
	public void testUnlockVault() throws BackendException, IOException {
		CloudType cloudType = Mockito.mock(CloudType.class);

		Mockito.when(cloud.type()).thenReturn(cloudType);

		Mockito.when(vault.getCloud()).thenReturn(cloud);
		Mockito.when(vault.getCloudType()).thenReturn(cloudType);
		Mockito.when(vault.getFormat()).thenReturn(8);
		Mockito.when(vault.getId()).thenReturn(25L);
		Mockito.when(vault.getName()).thenReturn("foo");
		Mockito.when(vault.getPath()).thenReturn("/foo");
		Mockito.when(vault.isUnlocked()).thenReturn(true);

		MasterkeyCryptoCloudProvider.UnlockTokenImpl unlockToken = new MasterkeyCryptoCloudProvider.UnlockTokenImpl(vault, masterkeyV7.getBytes(StandardCharsets.UTF_8));
		UnverifiedVaultConfig unverifiedVaultConfig = new UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", MASTERKEY_SCHEME, MASTERKEY_FILE_NAME)), MAX_VAULT_VERSION);

		Vault result = inTest.unlock(unlockToken, Optional.of(unverifiedVaultConfig), "foo", () -> false);

		MatcherAssert.assertThat(result.isUnlocked(), is(true));
		MatcherAssert.assertThat(result.getFormat(), is(8));
		MatcherAssert.assertThat(result.getMaxFileNameLength(), is(DEFAULT_MAX_FILE_NAME));

		Mockito.verify(inTest).cryptorFor(unlockToken.getKeyFile("foo"), SIV_CTRMAC);
		Mockito.verify(cryptoCloudContentRepositoryFactory).registerCryptor(Mockito.any(Vault.class), Mockito.any(Cryptor.class));
	}

	@Test
	@DisplayName("unlockLegacy(\"foo\")")
	public void testUnlockLegacyVault() throws BackendException, IOException {
		CloudType cloudType = Mockito.mock(CloudType.class);

		Mockito.when(cloud.type()).thenReturn(cloudType);

		Mockito.when(vault.getCloud()).thenReturn(cloud);
		Mockito.when(vault.getCloudType()).thenReturn(cloudType);
		Mockito.when(vault.getFormat()).thenReturn(7);
		Mockito.when(vault.getId()).thenReturn(25L);
		Mockito.when(vault.getName()).thenReturn("foo");
		Mockito.when(vault.getPath()).thenReturn("/foo");
		Mockito.when(vault.isUnlocked()).thenReturn(true);

		MasterkeyCryptoCloudProvider.UnlockTokenImpl unlockToken = new MasterkeyCryptoCloudProvider.UnlockTokenImpl(vault, masterkeyV7.getBytes(StandardCharsets.UTF_8));

		Vault result = inTest.unlock(unlockToken, Optional.empty(), "foo", () -> false);

		MatcherAssert.assertThat(result.isUnlocked(), is(true));
		MatcherAssert.assertThat(result.getFormat(), is(MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG));
		MatcherAssert.assertThat(result.getMaxFileNameLength(), is(DEFAULT_MAX_FILE_NAME));

		Mockito.verify(inTest).cryptorFor(unlockToken.getKeyFile("foo"), SIV_CTRMAC);
		Mockito.verify(cryptoCloudContentRepositoryFactory).registerCryptor(Mockito.any(Vault.class), Mockito.any(Cryptor.class));
	}

	@Test
	@DisplayName("unlockLegacyUsingNewVault(\"foo\")")
	public void testUnlockLegacyVaultUsingVaultFormat8() {
		UnlockToken unlockToken = new MasterkeyCryptoCloudProvider.UnlockTokenImpl(vault, masterkeyV8.getBytes(StandardCharsets.UTF_8));
		Assertions.assertThrows(UnsupportedVaultFormatException.class, () -> inTest.unlock(unlockToken, Optional.empty(), "foo", () -> false));
	}

	@DisplayName("changePassword(\"foo\")")
	@ParameterizedTest(name = "Legacy vault format {0}")
	@ValueSource(booleans = {true, false})
	public void tesChangePassword(boolean legacy) throws BackendException {
		if (legacy) {
			testChangePassword(masterkeyV7, Optional.empty());
		} else {
			UnverifiedVaultConfig unverifiedVaultConfig = new UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", MASTERKEY_SCHEME, MASTERKEY_FILE_NAME)), MAX_VAULT_VERSION);
			testChangePassword(masterkeyV8, Optional.of(unverifiedVaultConfig));
		}
	}

	private void testChangePassword(String masterkeyContent, Optional<UnverifiedVaultConfig> unverifiedVaultConfig) throws BackendException {
		TestFolder rootFolder = new RootTestFolder(cloud);
		TestFolder foo = new TestFolder(rootFolder, "foo", "/foo");
		TestFile vaultFile = new TestFile(foo, VAULT_FILE_NAME, "/foo/" + VAULT_FILE_NAME, Optional.empty(), Optional.empty());
		TestFile masterKeyFile = new TestFile(foo, MASTERKEY_FILE_NAME, "/foo/" + MASTERKEY_FILE_NAME, Optional.empty(), Optional.empty());

		Mockito.when(cloudContentRepository.file(foo, VAULT_FILE_NAME)).thenReturn(vaultFile);
		Mockito.when(cloudContentRepository.file(foo, MASTERKEY_FILE_NAME)).thenReturn(masterKeyFile);
		Mockito.when(cloudContentRepository.resolve(vault.getCloud(), vault.getPath())).thenReturn(foo);

		// 1. Read masterkey
		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream(masterkeyContent.getBytes()), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(masterKeyFile), Mockito.eq(Optional.empty()), Mockito.any(), Mockito.eq(NO_OP_PROGRESS_AWARE));

		// 2. Create backup
		String fileName = masterKeyFile.getName() + BackupFileIdSuffixGenerator.generate(masterkeyContent.getBytes()) + MASTERKEY_BACKUP_FILE_EXT;
		TestFile masterKeyBackupFile = new TestFile(foo, fileName, "/foo/" + fileName, Optional.empty(), Optional.empty());
		Mockito.when(cloudContentRepository.file(foo, fileName)).thenReturn(masterKeyBackupFile);

		Mockito.when(cloudContentRepository.write(Mockito.eq(masterKeyBackupFile), Mockito.any(DataSource.class), Mockito.eq(NO_OP_PROGRESS_AWARE), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String masterKeyFileContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).lines().collect(Collectors.joining());
			assertThat(masterKeyFileContent, is(masterkeyContent));
			return invocationOnMock.getArgument(0);
		});

		// 3. Create new Masterkey file
		String changedMasterkey;
		if (unverifiedVaultConfig.isPresent()) {
			changedMasterkey = "{  \"version\": 999,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"O8Z6ZP+aScORaOrMtWYrXjA5EptZk+IAYjEDEUJ7yIvGOWsR+CiwkA==\",  \"hmacMasterKey\": \"O8Z6ZP+aScORaOrMtWYrXjA5EptZk+IAYjEDEUJ7yIvGOWsR+CiwkA==\",  \"versionMac\": \"trDKXqDhu94/VPuoWaQGBm8hwSPYc0D9t6DRRxKZ65k=\"}";
		} else {
			changedMasterkey = "{  \"version\": 7,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"O8Z6ZP+aScORaOrMtWYrXjA5EptZk+IAYjEDEUJ7yIvGOWsR+CiwkA==\",  \"hmacMasterKey\": \"O8Z6ZP+aScORaOrMtWYrXjA5EptZk+IAYjEDEUJ7yIvGOWsR+CiwkA==\",  \"versionMac\": \"cn2sAK6l9p1/w9deJVUuW3h7br056mpv5srvALiYw+g=\"}";
		}
		Mockito.when(cloudContentRepository.write(Mockito.eq(masterKeyFile), Mockito.any(DataSource.class), Mockito.eq(NO_OP_PROGRESS_AWARE), Mockito.eq(true), Mockito.anyLong())).thenAnswer(invocationOnMock -> {
			DataSource in = invocationOnMock.getArgument(1);
			String masterKeyFileContent = new BufferedReader(new InputStreamReader(in.open(context), StandardCharsets.UTF_8)).lines().collect(Collectors.joining());
			assertThat(masterKeyFileContent, is(changedMasterkey));
			return invocationOnMock.getArgument(0);
		});

		inTest.changePassword(vault, unverifiedVaultConfig, "foo", "bar");

		Mockito.verify(cloudContentRepository).read(Mockito.eq(masterKeyFile), Mockito.eq(Optional.empty()), Mockito.any(), Mockito.eq(NO_OP_PROGRESS_AWARE));
		Mockito.verify(cloudContentRepository).write(Mockito.eq(masterKeyBackupFile), Mockito.any(DataSource.class), Mockito.eq(NO_OP_PROGRESS_AWARE), Mockito.eq(true), Mockito.anyLong());
		Mockito.verify(cloudContentRepository).write(Mockito.eq(masterKeyFile), Mockito.any(DataSource.class), Mockito.eq(NO_OP_PROGRESS_AWARE), Mockito.eq(true), Mockito.anyLong());
	}

	@DisplayName("isVaultPasswordValid(\"foo\", \"foo\")")
	@ParameterizedTest(name = "Legacy vault format {0}")
	@ValueSource(booleans = {true, false})
	public void testVaultPasswordVault(boolean legacy) throws BackendException, IOException {
		String password = "foo";
		if (legacy) {
			assertThat(testVaultPasswordVault(masterkeyV7, Optional.empty(), password), is(true));

			MasterkeyCryptoCloudProvider.UnlockTokenImpl unlockToken = new MasterkeyCryptoCloudProvider.UnlockTokenImpl(vault, masterkeyV7.getBytes(StandardCharsets.UTF_8));
			Mockito.verify(inTest).cryptorFor(unlockToken.getKeyFile(password), SIV_CTRMAC);
		} else {
			UnverifiedVaultConfig unverifiedVaultConfig = new UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", MASTERKEY_SCHEME, MASTERKEY_FILE_NAME)), MAX_VAULT_VERSION);
			assertThat(testVaultPasswordVault(masterkeyV8, Optional.of(unverifiedVaultConfig), password), is(true));

			MasterkeyCryptoCloudProvider.UnlockTokenImpl unlockToken = new MasterkeyCryptoCloudProvider.UnlockTokenImpl(vault, masterkeyV8.getBytes(StandardCharsets.UTF_8));
			Mockito.verify(inTest).cryptorFor(unlockToken.getKeyFile(password), SIV_CTRMAC);
		}
	}

	@DisplayName("isVaultPasswordValid(\"foo\", \"bar\")")
	@ParameterizedTest(name = "Legacy vault format {0}")
	@ValueSource(booleans = {true, false})
	public void testVaultPasswordVaultInvalidPassword(boolean legacy) throws BackendException, IOException {
		String password = "bar";
		if (legacy) {
			assertThat(testVaultPasswordVault(masterkeyV7, Optional.empty(), password), is(false));
		} else {
			UnverifiedVaultConfig unverifiedVaultConfig = new UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", MASTERKEY_SCHEME, MASTERKEY_FILE_NAME)), MAX_VAULT_VERSION);
			assertThat(testVaultPasswordVault(masterkeyV8, Optional.of(unverifiedVaultConfig), password), is(false));
		}
	}


	private boolean testVaultPasswordVault(String masterkeyContent, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, String password) throws BackendException {
		TestFolder rootFolder = new RootTestFolder(cloud);
		TestFolder foo = new TestFolder(rootFolder, "foo", "/foo");
		TestFile vaultFile = new TestFile(foo, VAULT_FILE_NAME, "/foo/" + VAULT_FILE_NAME, Optional.empty(), Optional.empty());
		TestFile masterKeyFile = new TestFile(foo, MASTERKEY_FILE_NAME, "/foo/" + MASTERKEY_FILE_NAME, Optional.empty(), Optional.empty());

		Mockito.when(cloudContentRepository.file(foo, VAULT_FILE_NAME)).thenReturn(vaultFile);
		Mockito.when(cloudContentRepository.file(foo, MASTERKEY_FILE_NAME)).thenReturn(masterKeyFile);
		Mockito.when(cloudContentRepository.resolve(vault.getCloud(), vault.getPath())).thenReturn(foo);


		Mockito.when(cloudContentRepository.file(foo, VAULT_FILE_NAME)).thenReturn(vaultFile);
		Mockito.when(cloudContentRepository.file(foo, MASTERKEY_FILE_NAME)).thenReturn(masterKeyFile);
		Mockito.when(cloudContentRepository.resolve(vault.getCloud(), vault.getPath())).thenReturn(foo);

		// 1. Read masterkey
		Mockito.doAnswer(invocation -> {
			OutputStream out = invocation.getArgument(2);
			CopyStream.copyStreamToStream(new ByteArrayInputStream(masterkeyContent.getBytes()), out);
			return null;
		}).when(cloudContentRepository).read(Mockito.eq(masterKeyFile), Mockito.eq(Optional.empty()), Mockito.any(), Mockito.eq(NO_OP_PROGRESS_AWARE));

		return inTest.isVaultPasswordValid(vault, unverifiedVaultConfig, password);
	}

}
