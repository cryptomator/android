package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileNameCryptor;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.cloud.DataSource;
import org.cryptomator.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.stream.Collectors;

import static org.cryptomator.cryptolib.api.Masterkey.SUBKEY_LEN_BYTES;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.DATA_DIR_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_FILE_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.ROOT_DIR_ID;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.VAULT_FILE_NAME;
import static org.cryptomator.domain.usecases.ProgressAware.NO_OP_PROGRESS_AWARE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MasterkeyCryptoCloudProviderTest {

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

		inTest = new MasterkeyCryptoCloudProvider(cloudContentRepository, cryptoCloudContentRepositoryFactory, secureRandom);
	}

	@Test
	@DisplayName("create(\"/foo, foo\")")
	public void testCreateVault() throws BackendException {
		String masterkey = "{  \"version\": 999,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"hmacMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"versionMac\": \"trDKXqDhu94/VPuoWaQGBm8hwSPYc0D9t6DRRxKZ65k=\"}";
		String vaultConfig = "eyJraWQiOiJtYXN0ZXJrZXlmaWxlOm1hc3RlcmtleS5jcnlwdG9tYXRvciIsImFsZyI6IkhTNTEyIn0.eyJtYXhGaWxlbmFtZUxlbiI6MjIwLCJmb3JtYXQiOjgsImNpcGhlckNvbWJvIjoiU0lWX0NUUk1BQyJ9.umiAcGObWuVISugrQu16hznDHIFM7moD1ukA1r5V1DRA0GjHQk1p6S9hkL0PaMD7xl04jSttMRalOYU1sg4wqQ";

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
			assertThat(masterKeyFileContent, is(masterkey));
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
	public void testLockVault() throws BackendException {
		// TODO implement me
		cryptoCloudContentRepositoryFactory.deregisterCryptor(vault);
	}

}
