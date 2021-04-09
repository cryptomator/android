package org.cryptomator.data.cloud.crypto;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.InvalidPassphraseException;
import org.cryptomator.cryptolib.api.Masterkey;
import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException;
import org.cryptomator.cryptolib.common.MasterkeyFileAccess;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.domain.usecases.vault.UnlockToken;
import org.cryptomator.util.Optional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.SecureRandom;
import java.text.Normalizer;

import static java.text.Normalizer.normalize;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.DATA_DIR_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.DEFAULT_CIPHER_COMBO;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.DEFAULT_MASTERKEY_FILE_VERSION;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.DEFAULT_MAX_FILE_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_BACKUP_FILE_EXT;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_FILE_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_SCHEME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MAX_VAULT_VERSION;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MIN_VAULT_VERSION;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.PEPPER;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.ROOT_DIR_ID;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.VAULT_FILE_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.VERSION_WITH_NORMALIZED_PASSWORDS;
import static org.cryptomator.data.cloud.crypto.VaultCipherCombo.SIV_CTRMAC;
import static org.cryptomator.domain.Vault.aCopyOf;
import static org.cryptomator.domain.usecases.ProgressAware.NO_OP_PROGRESS_AWARE;
import static org.cryptomator.util.Encodings.UTF_8;

public class MasterkeyCryptoCloudProvider implements CryptoCloudProvider {

	private final CloudContentRepository cloudContentRepository;
	private final CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory;
	private final SecureRandom secureRandom;

	public MasterkeyCryptoCloudProvider(CloudContentRepository cloudContentRepository, //
			CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory,
			SecureRandom secureRandom) {
		this.cloudContentRepository = cloudContentRepository;
		this.cryptoCloudContentRepositoryFactory = cryptoCloudContentRepositoryFactory;
		this.secureRandom = secureRandom;
	}

	@Override
	public void create(CloudFolder location, CharSequence password) throws BackendException {
		// Just for testing (id in VaultConfig is auto generated which makes sense while creating a vault but not for testing)
		create(location, password, VaultConfig.createVaultConfig());
	}

	// Visible for testing
	void create(CloudFolder location, CharSequence password, VaultConfig.VaultConfigBuilder vaultConfigBuilder) throws BackendException {
		// 1. write masterkey:
		Masterkey masterkey = Masterkey.generate(secureRandom);
		try (ByteArrayOutputStream data = new ByteArrayOutputStream()) {
			new MasterkeyFileAccess(PEPPER, secureRandom).persist(masterkey, data, password, DEFAULT_MASTERKEY_FILE_VERSION);
			cloudContentRepository.write(legacyMasterkeyFile(location), ByteArrayDataSource.from(data.toByteArray()), NO_OP_PROGRESS_AWARE, false, data.size());
		} catch (IOException e) {
			throw new FatalBackendException("Failed to write masterkey", e);
		}

		// 2. initialize vault:
		VaultConfig vaultConfig = vaultConfigBuilder //
				.vaultFormat(MAX_VAULT_VERSION) //
				.cipherCombo(DEFAULT_CIPHER_COMBO) //
				.keyId(URI.create(String.format("%s:%s", MASTERKEY_SCHEME, MASTERKEY_FILE_NAME))) //
				.maxFilenameLength(DEFAULT_MAX_FILE_NAME) //
				.build();

		byte[] encodedVaultConfig = vaultConfig.toToken(masterkey.getEncoded()).getBytes(UTF_8);
		CloudFile vaultFile = cloudContentRepository.file(location, VAULT_FILE_NAME);
		cloudContentRepository.write(vaultFile, ByteArrayDataSource.from(encodedVaultConfig), NO_OP_PROGRESS_AWARE, false, encodedVaultConfig.length);

		// 3. create root folder:
		createRootFolder(location, cryptorFor(masterkey, vaultConfig.getCipherCombo()));
	}

	private void createRootFolder(CloudFolder location, Cryptor cryptor) throws BackendException {
		CloudFolder dFolder = cloudContentRepository.folder(location, DATA_DIR_NAME);
		dFolder = cloudContentRepository.create(dFolder);
		String rootDirHash = cryptor.fileNameCryptor().hashDirectoryId(ROOT_DIR_ID);
		CloudFolder lvl1Folder = cloudContentRepository.folder(dFolder, rootDirHash.substring(0, 2));
		lvl1Folder = cloudContentRepository.create(lvl1Folder);
		CloudFolder lvl2Folder = cloudContentRepository.folder(lvl1Folder, rootDirHash.substring(2));
		cloudContentRepository.create(lvl2Folder);
	}

	@Override
	public Vault unlock(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password, Flag cancelledFlag) throws BackendException {
		return unlock(createUnlockToken(vault, unverifiedVaultConfig), unverifiedVaultConfig, password, cancelledFlag);
	}

	@Override
	public Vault unlock(UnlockToken token, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password, Flag cancelledFlag) throws BackendException {
		UnlockTokenImpl impl = (UnlockTokenImpl) token;
		try {
			Masterkey masterkey = impl.getKeyFile(password);

			int vaultFormat;
			int maxFileNameLength;
			Cryptor cryptor;

			if (unverifiedVaultConfig.isPresent()) {
				VaultConfig vaultConfig = VaultConfig.verify(masterkey.getEncoded(), unverifiedVaultConfig.get());
				vaultFormat = vaultConfig.getVaultFormat();
				assertVaultVersionIsSupported(vaultConfig.getVaultFormat());
				maxFileNameLength = vaultConfig.getMaxFilenameLength();
				cryptor = cryptorFor(masterkey, vaultConfig.getCipherCombo());
			} else {
				vaultFormat = MasterkeyFileAccess.readAllegedVaultVersion(impl.keyFileData);
				assertLegacyVaultVersionIsSupported(vaultFormat);
				maxFileNameLength = vaultFormat > 6 ? CryptoConstants.DEFAULT_MAX_FILE_NAME : CryptoImplVaultFormatPre7.MAX_FILE_NAME_LENGTH;
				cryptor = cryptorFor(masterkey, SIV_CTRMAC);
			}


			if (cancelledFlag.get()) {
				throw new CancellationException();
			}

			Vault vault = aCopyOf(token.getVault()) //
					.withUnlocked(true) //
					.withFormat(vaultFormat) //
					.withMaxFileNameLength(maxFileNameLength)
					.build();

			cryptoCloudContentRepositoryFactory.registerCryptor(vault, cryptor);

			return vault;
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	@Override
	public UnlockTokenImpl createUnlockToken(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig) throws BackendException {
		CloudFolder vaultLocation = vaultLocation(vault);
		if (unverifiedVaultConfig.isPresent()) {
			return createUnlockToken(vault, masterkeyFile(vaultLocation, unverifiedVaultConfig.get()));
		} else {
			return createUnlockToken(vault, legacyMasterkeyFile(vaultLocation));
		}
	}

	private CloudFile masterkeyFile(CloudFolder vaultLocation, UnverifiedVaultConfig unverifiedVaultConfig) throws BackendException {
		String path = unverifiedVaultConfig.getKeyId().getSchemeSpecificPart();
		// TODO / FIXME sanitize path and throw specific exception
		//throw new UnsupportedMasterkeyLocationException(unverifiedVaultConfig);
		return cloudContentRepository.file(vaultLocation, path);
	}

	private CloudFile legacyMasterkeyFile(CloudFolder location) throws BackendException {
		return cloudContentRepository.file(location, MASTERKEY_FILE_NAME);
	}

	private UnlockTokenImpl createUnlockToken(Vault vault, CloudFile location) throws BackendException {
		byte[] keyFileData = readKeyFileData(location);
		UnlockTokenImpl unlockToken = new UnlockTokenImpl(vault, keyFileData);
		return unlockToken;
	}

	private byte[] readKeyFileData(CloudFile masterkeyFile) throws BackendException {
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		cloudContentRepository.read(masterkeyFile, Optional.empty(), data, NO_OP_PROGRESS_AWARE);
		return data.toByteArray();
	}

	private Cryptor cryptorFor(Masterkey keyFile, VaultCipherCombo vaultCipherCombo) {
		return vaultCipherCombo.getCryptorProvider(secureRandom).withKey(keyFile);
	}

	@Override
	public boolean isVaultPasswordValid(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password) throws BackendException {
		try {
			// create a cryptor, which checks the password, then destroy it immediately
			Masterkey masterkey = createUnlockToken(vault, unverifiedVaultConfig).getKeyFile(password);
			VaultCipherCombo vaultCipherCombo;
			if(unverifiedVaultConfig.isPresent()) {
				VaultConfig vaultConfig = VaultConfig.verify(masterkey.getEncoded(), unverifiedVaultConfig.get());
				assertVaultVersionIsSupported(vaultConfig.getVaultFormat());
				vaultCipherCombo = vaultConfig.getCipherCombo();
			} else {
				int vaultVersion = MasterkeyFileAccess.readAllegedVaultVersion(masterkey.getEncoded());
				assertLegacyVaultVersionIsSupported(vaultVersion);
				vaultCipherCombo = SIV_CTRMAC;
			}
			cryptorFor(masterkey, vaultCipherCombo).destroy();
			return true;
		} catch (InvalidPassphraseException e) {
			return false;
		} catch (IOException e) {
			throw new FatalBackendException(e);
		}
	}

	@Override
	public void lock(Vault vault) {
		cryptoCloudContentRepositoryFactory.deregisterCryptor(vault);
	}

	private void assertVaultVersionIsSupported(int version) {
		if (version < MIN_VAULT_VERSION) {
			throw new UnsupportedVaultFormatException(version, MIN_VAULT_VERSION);
		} else if (version > MAX_VAULT_VERSION) {
			throw new UnsupportedVaultFormatException(version, MAX_VAULT_VERSION);
		}
	}

	private void assertLegacyVaultVersionIsSupported(int version) {
		if (version < MIN_VAULT_VERSION) {
			throw new UnsupportedVaultFormatException(version, MIN_VAULT_VERSION);
		} else if (version > MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG) {
			throw new UnsupportedVaultFormatException(version, MAX_VAULT_VERSION_WITHOUT_VAULT_CONFIG);
		}
	}

	@Override
	public void changePassword(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, String oldPassword, String newPassword) throws BackendException {
		CloudFolder vaultLocation = vaultLocation(vault);
		ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();

		CloudFile masterkeyFile;
		if (unverifiedVaultConfig.isPresent()) {
			masterkeyFile = masterkeyFile(vaultLocation, unverifiedVaultConfig.get());
		} else {
			masterkeyFile = legacyMasterkeyFile(vaultLocation);
		}

		cloudContentRepository.read(masterkeyFile, Optional.empty(), dataOutputStream, NO_OP_PROGRESS_AWARE);
		byte[] data = dataOutputStream.toByteArray();

		int vaultVersion;
		if (unverifiedVaultConfig.isPresent()) {
			vaultVersion = unverifiedVaultConfig.get().getVaultFormat();
			assertVaultVersionIsSupported(vaultVersion);
		} else {
			try {
				vaultVersion = MasterkeyFileAccess.readAllegedVaultVersion(data);
				assertLegacyVaultVersionIsSupported(vaultVersion);
			} catch (IOException e) {
				throw new FatalBackendException("Failed to read legacy vault version", e);
			}
		}

		createBackupMasterKeyFile(data, masterkeyFile);
		createNewMasterKeyFile(data, vaultVersion, oldPassword, newPassword, masterkeyFile);
	}

	private CloudFolder vaultLocation(Vault vault) throws BackendException {
		return cloudContentRepository.resolve(vault.getCloud(), vault.getPath());
	}

	private void createBackupMasterKeyFile(byte[] data, CloudFile masterkeyFile) throws BackendException {
		cloudContentRepository.write(masterkeyBackupFile(masterkeyFile, data), ByteArrayDataSource.from(data), NO_OP_PROGRESS_AWARE, true, data.length);
	}

	private CloudFile masterkeyBackupFile(CloudFile masterkeyFile, byte[] data) throws BackendException {
		String fileName = masterkeyFile.getName() + BackupFileIdSuffixGenerator.generate(data) + MASTERKEY_BACKUP_FILE_EXT;
		return cloudContentRepository.file(masterkeyFile.getParent(), fileName);
	}

	private void createNewMasterKeyFile(byte[] data, int vaultVersion, String oldPassword, String newPassword, CloudFile masterkeyFile) throws BackendException {
		try {
			byte[] newMasterKeyFile = new MasterkeyFileAccess(PEPPER, secureRandom) //
					.changePassphrase(data, normalizePassword(oldPassword, vaultVersion), normalizePassword(newPassword, vaultVersion));
			cloudContentRepository.write(masterkeyFile, //
					ByteArrayDataSource.from(newMasterKeyFile), //
					NO_OP_PROGRESS_AWARE, //
					true, //
					newMasterKeyFile.length);
		} catch (IOException e) {
			throw new FatalBackendException("Failed to read legacy vault version", e);
		}
	}

	private CharSequence normalizePassword(CharSequence password, int vaultVersion) {
		if (vaultVersion >= VERSION_WITH_NORMALIZED_PASSWORDS) {
			return normalize(password, Normalizer.Form.NFC);
		} else {
			return password;
		}
	}

	private static class UnlockTokenImpl implements UnlockToken {

		private final Vault vault;
		private final byte[] keyFileData;

		private UnlockTokenImpl(Vault vault, byte[] keyFileData) {
			this.vault = vault;
			this.keyFileData = keyFileData;
		}

		@Override
		public Vault getVault() {
			return vault;
		}

		public Masterkey getKeyFile(CharSequence password) throws IOException {
			return new MasterkeyFileAccess(PEPPER, new SecureRandom()).load(new ByteArrayInputStream(keyFileData), password);
		}
	}

}
