package org.cryptomator.data.cloud.crypto

import android.content.Context
import com.google.common.base.Optional
import com.google.common.io.BaseEncoding
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.cryptolib.api.CryptorProvider
import org.cryptomator.cryptolib.api.FileNameCryptor
import org.cryptomator.cryptolib.api.InvalidPassphraseException
import org.cryptomator.cryptolib.api.Masterkey
import org.cryptomator.cryptolib.common.MasterkeyFileAccess
import org.cryptomator.data.cloud.crypto.BackupFileIdSuffixGenerator.generate
import org.cryptomator.data.cloud.crypto.MasterkeyCryptoCloudProvider.UnlockTokenImpl
import org.cryptomator.data.cloud.crypto.VaultConfig.VaultConfigBuilder
import org.cryptomator.data.util.CopyStream.copyStreamToStream
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.UnverifiedVaultConfig
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.vaultconfig.MissingVaultConfigFileException
import org.cryptomator.domain.exception.vaultconfig.VaultKeyInvalidException
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.vault.UnlockToken
import org.hamcrest.CoreMatchers
import org.hamcrest.MatcherAssert
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.stream.Collectors

internal class MasterkeyCryptoCloudProviderTest {

	private val masterkeyV8 =
		"{  \"version\": 999,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"hmacMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"versionMac\": \"trDKXqDhu94/VPuoWaQGBm8hwSPYc0D9t6DRRxKZ65k=\"}"
	private val masterkeyV7 =
		"{  \"version\": 7,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"hmacMasterKey\": \"D2kc+xBoAcVY+M7s74YBEy6l7ga2+Nz+HS5o0TQY3JMW1uQ5jTlLIQ==\",  \"versionMac\": \"cn2sAK6l9p1/w9deJVUuW3h7br056mpv5srvALiYw+g=\"}"
	private val vaultConfig =
		"eyJraWQiOiJtYXN0ZXJrZXlmaWxlOm1hc3RlcmtleS5jcnlwdG9tYXRvciIsImFsZyI6IkhTMjU2IiwidHlwIjoiSldUIn0.eyJqdGkiOiIiLCJmb3JtYXQiOjgsImNpcGhlckNvbWJvIjoiU0lWX0dDTSIsInNob3J0ZW5pbmdUaHJlc2hvbGQiOjIyMH0.HHMkCqK-kFv_TXAhrzgp8vuPhws_2NswhaAHB9hkKBs"

	private val vaultConfigWithBrokenSignature = vaultConfig.substringBeforeLast('.') + ".I" + vaultConfig.substringAfterLast('.').drop(1)

	private var context: Context = mock()
	private var cloud: Cloud = mock()
	private var cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> = mock()
	private var cryptoCloudContentRepositoryFactory: CryptoCloudContentRepositoryFactory = mock()
	private var vault: Vault = mock()
	private var cryptor: Cryptor = mock()
	private var fileNameCryptor: FileNameCryptor = mock()
	private var secureRandom: SecureRandom = mock()

	private lateinit var inTest: MasterkeyCryptoCloudProvider

	private fun <T> any(type: Class<T>): T = Mockito.any(type)

	@BeforeEach
	fun setUp() {
		whenever(cryptor.fileNameCryptor()).thenReturn(fileNameCryptor)

		val key = ByteArray(Masterkey.SUBKEY_LEN_BYTES + Masterkey.SUBKEY_LEN_BYTES)
		doNothing().whenever(secureRandom).nextBytes(key)

		inTest = spy(MasterkeyCryptoCloudProvider(cloudContentRepository, cryptoCloudContentRepositoryFactory, secureRandom))
	}

	@Test
	@DisplayName("create(\"/foo\", \"foo\")")
	@Throws(BackendException::class)
	fun testCreateVault() {
		val rootFolder = RootTestFolder(cloud)
		val foo = TestFolder(rootFolder, "foo", "/foo")
		val vaultFile = TestFile(foo, CryptoConstants.VAULT_FILE_NAME, "/foo/" + CryptoConstants.VAULT_FILE_NAME, null, null)
		val masterKeyFile = TestFile(foo, CryptoConstants.MASTERKEY_FILE_NAME, "/foo/" + CryptoConstants.MASTERKEY_FILE_NAME, null, null)
		whenever(cloudContentRepository.file(foo, CryptoConstants.VAULT_FILE_NAME)).thenReturn(vaultFile)
		whenever(cloudContentRepository.file(foo, CryptoConstants.MASTERKEY_FILE_NAME)).thenReturn(masterKeyFile)

		// 1. write masterkey
		whenever(cloudContentRepository.write(eq(masterKeyFile), any(DataSource::class.java), eq(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD), eq(false), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val masterKeyFileContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).lines().collect(Collectors.joining())
				MatcherAssert.assertThat(masterKeyFileContent, CoreMatchers.`is`(masterkeyV8))
				invocationOnMock.getArgument(0)
			}

		// 2. initialize vault
		whenever(cloudContentRepository.write(eq(vaultFile), any(DataSource::class.java), eq(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD), eq(false), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val vaultConfigFileContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).lines().collect(Collectors.joining())
				MatcherAssert.assertThat(vaultConfigFileContent, CoreMatchers.`is`(vaultConfig))
				invocationOnMock.getArgument(0)
			}

		// 3. create root folder
		val rootDirHash = "KG6TFDGKXGZEGWRZOGTDFDF4YEGAZO6Q"
		val dFolder = TestFolder(foo, "d", "/foo/" + CryptoConstants.DATA_DIR_NAME)
		val lvl1Dir = TestFolder(dFolder, rootDirHash.substring(0, 2), "/foo/" + CryptoConstants.DATA_DIR_NAME + "/" + rootDirHash.substring(0, 2))
		val lvl2Dir = TestFolder(lvl1Dir, rootDirHash.substring(2), "/foo/" + CryptoConstants.DATA_DIR_NAME + "/" + rootDirHash.substring(0, 2) + "/" + rootDirHash.substring(2))

		whenever(cloudContentRepository.folder(foo, CryptoConstants.DATA_DIR_NAME)).thenReturn(dFolder)
		whenever(cloudContentRepository.create(dFolder)).thenReturn(dFolder)
		whenever(cryptor.fileNameCryptor().hashDirectoryId(CryptoConstants.ROOT_DIR_ID)).thenReturn(CryptoConstants.ROOT_DIR_ID)
		whenever(cloudContentRepository.folder(dFolder, lvl1Dir.name)).thenReturn(lvl1Dir)
		whenever(cloudContentRepository.create(lvl1Dir)).thenReturn(lvl1Dir)
		whenever(cloudContentRepository.folder(lvl1Dir, lvl2Dir.name)).thenReturn(lvl2Dir)
		whenever(cloudContentRepository.create(lvl2Dir)).thenReturn(lvl2Dir)

		val recoveryKey = inTest.create(foo, "foo", VaultConfigBuilder().id(""))

		MatcherAssert.assertThat(recoveryKey, CoreMatchers.`is`("ad ".repeat(42) + "at assume"))
		val usedMasterkey = argumentCaptor<Masterkey>()
		verify(inTest).cryptorFor(usedMasterkey.capture(), any())
		MatcherAssert.assertThat(usedMasterkey.firstValue.isDestroyed, CoreMatchers.`is`(true))

		verify(cloudContentRepository).write(
			eq(masterKeyFile), any(
				DataSource::class.java
			), eq(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD), eq(false), any()
		)
		verify(cloudContentRepository).write(
			eq(vaultFile), any(
				DataSource::class.java
			), eq(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD), eq(false), any()
		)

		verify(cloudContentRepository).create(dFolder)
		verify(cloudContentRepository).create(lvl1Dir)
		verify(cloudContentRepository).create(lvl2Dir)
	}

	@Test
	@DisplayName("lock(\"foo\")")
	fun testLockVault() {
		inTest.lock(vault)
		verify(cryptoCloudContentRepositoryFactory).deregisterCryptor(vault)
	}

	@Test
	@DisplayName("unlock(\"foo\")")
	@Throws(BackendException::class, IOException::class)
	fun testUnlockVault() {
		val cloudType: CloudType = mock()

		whenever(cloud.type()).thenReturn(cloudType)
		whenever(vault.cloud).thenReturn(cloud)
		whenever(vault.cloudType).thenReturn(cloudType)
		whenever(vault.format).thenReturn(8)
		whenever(vault.id).thenReturn(25L)
		whenever(vault.name).thenReturn("foo")
		whenever(vault.path).thenReturn("/foo")
		whenever(vault.isUnlocked).thenReturn(true)

		val unlockToken = UnlockTokenImpl(vault, secureRandom, masterkeyV7.toByteArray(StandardCharsets.UTF_8))
		val unverifiedVaultConfig = UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", CryptoConstants.MASTERKEY_SCHEME, CryptoConstants.MASTERKEY_FILE_NAME)), CryptoConstants.MAX_VAULT_VERSION)
		val result: Vault = inTest.unlock(unlockToken, Optional.of(unverifiedVaultConfig), "foo") { false }

		MatcherAssert.assertThat(result.isUnlocked, CoreMatchers.`is`(true))
		MatcherAssert.assertThat(result.format, CoreMatchers.`is`(8))
		MatcherAssert.assertThat(result.shorteningThreshold, CoreMatchers.`is`(CryptoConstants.DEFAULT_MAX_FILE_NAME))

		verify(inTest).cryptorFor(unlockToken.getKeyFile("foo"), CryptorProvider.Scheme.SIV_GCM)
		verify(cryptoCloudContentRepositoryFactory).registerCryptor(any(Vault::class.java), any(Cryptor::class.java))
	}

	@Test
	@DisplayName("unlockLegacy(\"foo\")")
	@Throws(BackendException::class, IOException::class)
	fun testUnlockLegacyVault() {
		val cloudType: CloudType = mock()

		whenever(cloud.type()).thenReturn(cloudType)
		whenever(vault.cloud).thenReturn(cloud)
		whenever(vault.cloudType).thenReturn(cloudType)
		whenever(vault.format).thenReturn(7)
		whenever(vault.id).thenReturn(25L)
		whenever(vault.name).thenReturn("foo")
		whenever(vault.path).thenReturn("/foo")
		whenever(vault.isUnlocked).thenReturn(true)

		val unlockToken = UnlockTokenImpl(vault, secureRandom, masterkeyV7.toByteArray(StandardCharsets.UTF_8))
		val result = inTest.unlock(unlockToken, Optional.absent(), "foo", { false })

		MatcherAssert.assertThat(result.isUnlocked, CoreMatchers.`is`(true))
		MatcherAssert.assertThat(result.format, CoreMatchers.`is`(CryptoConstants.MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG))
		MatcherAssert.assertThat(result.shorteningThreshold, CoreMatchers.`is`(CryptoConstants.DEFAULT_MAX_FILE_NAME))

		verify(inTest).cryptorFor(unlockToken.getKeyFile("foo"), CryptorProvider.Scheme.SIV_CTRMAC)
		verify(cryptoCloudContentRepositoryFactory).registerCryptor(any(Vault::class.java), any(Cryptor::class.java))
	}

	@Test
	@DisplayName("unlockLegacyUsingNewVault(\"foo\")")
	fun testUnlockLegacyVaultUsingVaultFormat8() {
		val unlockToken: UnlockToken = UnlockTokenImpl(vault, secureRandom, masterkeyV8.toByteArray(StandardCharsets.UTF_8))
		Assertions.assertThrows(MissingVaultConfigFileException::class.java) { inTest.unlock(unlockToken, Optional.absent(), "foo", { false }) }
	}

	@DisplayName("changePassword(\"foo\")")
	@ParameterizedTest(name = "Legacy vault format {0}")
	@ValueSource(booleans = [true, false])
	@Throws(BackendException::class)
	fun tesChangePassword(legacy: Boolean) {
		val cloudType: CloudType = mock()

		whenever(cloud.type()).thenReturn(cloudType)
		whenever(vault.cloud).thenReturn(cloud)
		whenever(vault.cloudType).thenReturn(cloudType)
		whenever(vault.format).thenReturn(7)
		whenever(vault.id).thenReturn(25L)
		whenever(vault.name).thenReturn("foo")
		whenever(vault.path).thenReturn("/foo")
		whenever(vault.isUnlocked).thenReturn(true)

		if (legacy) {
			testChangePassword(masterkeyV7, Optional.absent())
		} else {
			val unverifiedVaultConfig = UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", CryptoConstants.MASTERKEY_SCHEME, CryptoConstants.MASTERKEY_FILE_NAME)), CryptoConstants.MAX_VAULT_VERSION)
			testChangePassword(masterkeyV8, Optional.of(unverifiedVaultConfig))
		}
	}

	@Throws(BackendException::class)
	private fun testChangePassword(masterkeyContent: String, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>) {
		val rootFolder: TestFolder = RootTestFolder(cloud)
		val foo = TestFolder(rootFolder, "foo", "/foo")
		val vaultFile = TestFile(foo, CryptoConstants.VAULT_FILE_NAME, "/foo/" + CryptoConstants.VAULT_FILE_NAME, null, null)
		val masterKeyFile = TestFile(foo, CryptoConstants.MASTERKEY_FILE_NAME, "/foo/" + CryptoConstants.MASTERKEY_FILE_NAME, null, null)
		whenever(cloudContentRepository.file(foo, CryptoConstants.VAULT_FILE_NAME)).thenReturn(vaultFile)
		whenever(cloudContentRepository.file(foo, CryptoConstants.MASTERKEY_FILE_NAME)).thenReturn(masterKeyFile)
		whenever(cloudContentRepository.resolve(vault.cloud, vault.path)).thenReturn(foo)

		// 1. Read masterkey
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(1)
			copyStreamToStream(ByteArrayInputStream(masterkeyContent.toByteArray()), out)
			null
		}.`when`(cloudContentRepository).read(eq(masterKeyFile), any(), eq(ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD))

		// 2. Create backup
		val fileName: String = masterKeyFile.name + generate(masterkeyContent.toByteArray()) + CryptoConstants.MASTERKEY_BACKUP_FILE_EXT
		val masterKeyBackupFile = TestFile(foo, fileName, "/foo/$fileName", null, null)

		whenever(cloudContentRepository.file(foo, fileName)).thenReturn(masterKeyBackupFile)
		whenever(cloudContentRepository.write(eq(masterKeyBackupFile), any(DataSource::class.java), eq(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD), eq(true), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val masterKeyFileContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).lines().collect(Collectors.joining())
				MatcherAssert.assertThat(masterKeyFileContent, CoreMatchers.`is`(masterkeyContent))
				invocationOnMock.getArgument(0)
			}

		// 3. Create new Masterkey file
		val changedMasterkey = if (unverifiedVaultConfig.isPresent) {
			"{  \"version\": 999,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"O8Z6ZP+aScORaOrMtWYrXjA5EptZk+IAYjEDEUJ7yIvGOWsR+CiwkA==\",  \"hmacMasterKey\": \"O8Z6ZP+aScORaOrMtWYrXjA5EptZk+IAYjEDEUJ7yIvGOWsR+CiwkA==\",  \"versionMac\": \"trDKXqDhu94/VPuoWaQGBm8hwSPYc0D9t6DRRxKZ65k=\"}"
		} else {
			"{  \"version\": 7,  \"scryptSalt\": \"AAAAAAAAAAA=\",  \"scryptCostParam\": 32768,  \"scryptBlockSize\": 8,  \"primaryMasterKey\": \"O8Z6ZP+aScORaOrMtWYrXjA5EptZk+IAYjEDEUJ7yIvGOWsR+CiwkA==\",  \"hmacMasterKey\": \"O8Z6ZP+aScORaOrMtWYrXjA5EptZk+IAYjEDEUJ7yIvGOWsR+CiwkA==\",  \"versionMac\": \"cn2sAK6l9p1/w9deJVUuW3h7br056mpv5srvALiYw+g=\"}"
		}
		whenever(cloudContentRepository.write(eq(masterKeyFile), any(DataSource::class.java), eq(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD), eq(true), any()))
			.thenAnswer { invocationOnMock: InvocationOnMock ->
				val inputStream = invocationOnMock.getArgument<DataSource>(1)
				val masterKeyFileContent = BufferedReader(InputStreamReader(inputStream.open(context)!!, StandardCharsets.UTF_8)).lines().collect(Collectors.joining())
				MatcherAssert.assertThat(masterKeyFileContent, CoreMatchers.`is`(changedMasterkey))
				invocationOnMock.getArgument(0)
			}

		inTest.changePassword(vault, unverifiedVaultConfig, "foo", "bar")

		Mockito.verify(cloudContentRepository).read(eq(masterKeyFile), any(), eq(ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD))
		Mockito.verify(cloudContentRepository).write(
			eq(masterKeyBackupFile), any(
				DataSource::class.java
			), eq(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD), eq(true), any()
		)
		Mockito.verify(cloudContentRepository).write(
			eq(masterKeyFile), any(
				DataSource::class.java
			), eq(ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD), eq(true), any()
		)
	}

	@DisplayName("isVaultPasswordValid(\"foo\", \"foo\")")
	@ParameterizedTest(name = "Legacy vault format {0}")
	@ValueSource(booleans = [true, false])
	@Throws(BackendException::class, IOException::class)
	fun testVaultPasswordVault(legacy: Boolean) {
		val password = "foo"
		if (legacy) {
			MatcherAssert.assertThat(testVaultPasswordVault(masterkeyV7, Optional.absent(), password), CoreMatchers.`is`(true))

			val unlockToken = UnlockTokenImpl(vault, secureRandom, masterkeyV7.toByteArray(StandardCharsets.UTF_8))

			Mockito.verify(inTest).cryptorFor(unlockToken.getKeyFile(password), CryptorProvider.Scheme.SIV_CTRMAC)
		} else {
			val unverifiedVaultConfig = UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", CryptoConstants.MASTERKEY_SCHEME, CryptoConstants.MASTERKEY_FILE_NAME)), CryptoConstants.MAX_VAULT_VERSION)

			MatcherAssert.assertThat(testVaultPasswordVault(masterkeyV8, Optional.of(unverifiedVaultConfig), password), CoreMatchers.`is`(true))

			val unlockToken = UnlockTokenImpl(vault, secureRandom, masterkeyV8.toByteArray(StandardCharsets.UTF_8))

			Mockito.verify(inTest).cryptorFor(unlockToken.getKeyFile(password), CryptorProvider.Scheme.SIV_GCM)
		}
	}

	@DisplayName("isVaultPasswordValid(\"foo\", \"bar\")")
	@ParameterizedTest(name = "Legacy vault format {0}")
	@ValueSource(booleans = [true, false])
	@Throws(BackendException::class, IOException::class)
	fun testVaultPasswordVaultInvalidPassword(legacy: Boolean) {
		val password = "bar"
		if (legacy) {
			MatcherAssert.assertThat(testVaultPasswordVault(masterkeyV7, Optional.absent(), password), CoreMatchers.`is`(false))
		} else {
			val unverifiedVaultConfig = UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", CryptoConstants.MASTERKEY_SCHEME, CryptoConstants.MASTERKEY_FILE_NAME)), CryptoConstants.MAX_VAULT_VERSION)
			MatcherAssert.assertThat(testVaultPasswordVault(masterkeyV8, Optional.of(unverifiedVaultConfig), password), CoreMatchers.`is`(false))
		}
	}

	@DisplayName("recoveryKey(\"foo\", \"foo\")")
	@ParameterizedTest(name = "Legacy vault format {0}")
	@ValueSource(booleans = [true, false])
	@Throws(BackendException::class)
	fun testRecoveryKey(legacy: Boolean) {
		stubVaultWithMasterkeyFile(if (legacy) masterkeyV7 else masterkeyV8)

		val recoveryKey = inTest.recoveryKey(vault, unverifiedVaultConfig(legacy, vaultConfig), "foo")

		MatcherAssert.assertThat(recoveryKey, CoreMatchers.`is`("ad ".repeat(42) + "at assume"))
	}

	@DisplayName("recoveryKey(\"foo\", \"bar\")")
	@ParameterizedTest(name = "Legacy vault format {0}")
	@ValueSource(booleans = [true, false])
	@Throws(BackendException::class)
	fun testRecoveryKeyInvalidPassword(legacy: Boolean) {
		stubVaultWithMasterkeyFile(if (legacy) masterkeyV7 else masterkeyV8)

		Assertions.assertThrows(InvalidPassphraseException::class.java) {
			inTest.recoveryKey(vault, unverifiedVaultConfig(legacy, vaultConfig), "bar")
		}
	}

	@Test
	@DisplayName("recoveryKey() of the masterkey behind the recovery key test vector of Cryptomator for desktop")
	@Throws(BackendException::class)
	fun testRecoveryKeyOfNonZeroMasterkey() {
		val rawKey = BaseEncoding.base16().lowerCase().decode("bb01e256b7836e6bf8ecaee867397065b1dc12a976676f5badb805c4a03b8b9e295d53e81e8c4a2b9af3652dd51213f11de810282c1ae4c9387a9eceb18bad03")
		val masterkeyFile = ByteArrayOutputStream().use { out ->
			MasterkeyFileAccess(CryptoConstants.PEPPER, SecureRandom()).persist(Masterkey(rawKey), out, "foo", CryptoConstants.MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG)
			out.toString(StandardCharsets.UTF_8.name())
		}
		stubVaultWithMasterkeyFile(masterkeyFile)

		val recoveryKey = inTest.recoveryKey(vault, Optional.absent(), "foo")

		MatcherAssert.assertThat(
			recoveryKey, CoreMatchers.`is`(
				"pathway lift abuse plenty export texture gentleman landscape beyond ceiling around leaf cafe charity border breakdown victory surely computer cat linger restrict " //
						+ "infer crowd live computer true written amazed investor boot depth left theory snow whereby terminal weekly reject happiness circuit partial cup ad"
			)
		)
	}

	@Test
	@DisplayName("recoveryKey() destroys the loaded masterkey")
	@Throws(BackendException::class)
	fun testRecoveryKeyDestroysMasterkey() {
		val masterkey = Masterkey(ByteArray(64))
		val unlockToken: UnlockTokenImpl = mock()
		whenever(unlockToken.getKeyFile("foo")).thenReturn(masterkey)
		Mockito.doReturn(unlockToken).`when`(inTest).createUnlockToken(vault, Optional.absent())

		inTest.recoveryKey(vault, Optional.absent(), "foo")

		MatcherAssert.assertThat(masterkey.isDestroyed, CoreMatchers.`is`(true))
	}

	@Test
	@DisplayName("isVaultPasswordValid() destroys the loaded masterkey when the vault config does not match")
	@Throws(BackendException::class)
	fun testVaultPasswordValidDestroysMasterkeyOnFailure() {
		val masterkey = Masterkey(ByteArray(64))
		val unlockToken: UnlockTokenImpl = mock()
		whenever(unlockToken.getKeyFile("foo")).thenReturn(masterkey)
		val brokenVaultConfig = unverifiedVaultConfig(false, vaultConfigWithBrokenSignature)
		Mockito.doReturn(unlockToken).`when`(inTest).createUnlockToken(vault, brokenVaultConfig)

		Assertions.assertThrows(VaultKeyInvalidException::class.java) {
			inTest.isVaultPasswordValid(vault, brokenVaultConfig, "foo")
		}

		MatcherAssert.assertThat(masterkey.isDestroyed, CoreMatchers.`is`(true))
	}

	@Test
	@DisplayName("recoveryKey() of a masterkey that did not sign the vault config")
	@Throws(BackendException::class)
	fun testRecoveryKeyOfMasterkeyNotMatchingVaultConfig() {
		stubVaultWithMasterkeyFile(masterkeyV8)

		Assertions.assertThrows(VaultKeyInvalidException::class.java) {
			inTest.recoveryKey(vault, unverifiedVaultConfig(false, vaultConfigWithBrokenSignature), "foo")
		}
	}

	private fun unverifiedVaultConfig(legacy: Boolean, vaultConfig: String): Optional<UnverifiedVaultConfig> {
		return if (legacy) {
			Optional.absent()
		} else {
			Optional.of(UnverifiedVaultConfig(vaultConfig, URI.create(String.format("%s:%s", CryptoConstants.MASTERKEY_SCHEME, CryptoConstants.MASTERKEY_FILE_NAME)), CryptoConstants.MAX_VAULT_VERSION))
		}
	}

	@Throws(BackendException::class)
	private fun testVaultPasswordVault(masterkeyContent: String, unverifiedVaultConfig: Optional<UnverifiedVaultConfig>, password: String): Boolean {
		stubVaultWithMasterkeyFile(masterkeyContent)
		return inTest.isVaultPasswordValid(vault, unverifiedVaultConfig, password)
	}

	@Throws(BackendException::class)
	private fun stubVaultWithMasterkeyFile(masterkeyContent: String) {
		val cloudType: CloudType = mock()

		whenever(cloud.type()).thenReturn(cloudType)
		whenever(vault.cloud).thenReturn(cloud)
		whenever(vault.cloudType).thenReturn(cloudType)
		whenever(vault.format).thenReturn(7)
		whenever(vault.id).thenReturn(25L)
		whenever(vault.name).thenReturn("foo")
		whenever(vault.path).thenReturn("/foo")
		whenever(vault.isUnlocked).thenReturn(true)

		val rootFolder: TestFolder = RootTestFolder(cloud)
		val foo = TestFolder(rootFolder, "foo", "/foo")
		val vaultFile = TestFile(foo, CryptoConstants.VAULT_FILE_NAME, "/foo/" + CryptoConstants.VAULT_FILE_NAME, null, null)
		val masterKeyFile = TestFile(foo, CryptoConstants.MASTERKEY_FILE_NAME, "/foo/" + CryptoConstants.MASTERKEY_FILE_NAME, null, null)

		whenever(cloudContentRepository.file(foo, CryptoConstants.VAULT_FILE_NAME)).thenReturn(vaultFile)
		whenever(cloudContentRepository.file(foo, CryptoConstants.MASTERKEY_FILE_NAME)).thenReturn(masterKeyFile)
		whenever(cloudContentRepository.resolve(vault.cloud, vault.path)).thenReturn(foo)
		whenever(cloudContentRepository.file(foo, CryptoConstants.VAULT_FILE_NAME)).thenReturn(vaultFile)
		whenever(cloudContentRepository.file(foo, CryptoConstants.MASTERKEY_FILE_NAME)).thenReturn(masterKeyFile)
		whenever(cloudContentRepository.resolve(vault.cloud, vault.path)).thenReturn(foo)

		// 1. Read masterkey
		Mockito.doAnswer { invocation: InvocationOnMock ->
			val out = invocation.getArgument<OutputStream>(1)
			copyStreamToStream(ByteArrayInputStream(masterkeyContent.toByteArray()), out)
			null
		}.`when`(cloudContentRepository).read(eq(masterKeyFile), any(), eq(ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD))
	}
}
