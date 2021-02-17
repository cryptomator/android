package org.cryptomator.data.cloud.crypto;

import static android.R.attr.version;
import static java.text.Normalizer.normalize;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.DATA_DIR_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_BACKUP_FILE_EXT;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MASTERKEY_FILE_NAME;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MAX_VAULT_VERSION;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.MIN_VAULT_VERSION;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.ROOT_DIR_ID;
import static org.cryptomator.data.cloud.crypto.CryptoConstants.VERSION_WITH_NORMALIZED_PASSWORDS;
import static org.cryptomator.domain.Vault.aCopyOf;
import static org.cryptomator.domain.usecases.ProgressAware.NO_OP_PROGRESS_AWARE;

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.cryptomator.cryptolib.Cryptors;
import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.CryptorProvider;
import org.cryptomator.cryptolib.api.InvalidPassphraseException;
import org.cryptomator.cryptolib.api.KeyFile;
import org.cryptomator.cryptolib.api.UnsupportedVaultFormatException;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.CancellationException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.cloud.ByteArrayDataSource;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.domain.usecases.vault.UnlockToken;
import org.cryptomator.util.Optional;

@Singleton
public class CryptoCloudFactory {

	private final CryptorProvider cryptorProvider;
	private final CloudContentRepository cloudContentRepository;
	private final CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory;

	@Inject
	public CryptoCloudFactory( //
			CloudContentRepository cloudContentRepository, //
			CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory, //
			CryptorProvider cryptorProvider) {
		this.cryptorProvider = cryptorProvider;
		this.cloudContentRepository = cloudContentRepository;
		this.cryptoCloudContentRepositoryFactory = cryptoCloudContentRepositoryFactory;
	}

	public void create(CloudFolder location, CharSequence password) throws BackendException {
		Cryptor cryptor = cryptorProvider.createNew();
		try {
			KeyFile keyFile = cryptor.writeKeysToMasterkeyFile(normalizePassword(password, version), MAX_VAULT_VERSION);
			writeKeyFile(location, keyFile);
			createRootFolder(location, cryptor);
		} finally {
			cryptor.destroy();
		}
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

	public Cloud decryptedViewOf(Vault vault) throws BackendException {
		return new CryptoCloud(aCopyOf(vault).build());
	}

	public Vault unlock(Vault vault, CharSequence password, Flag cancelledFlag) throws BackendException {
		return unlock(createUnlockToken(vault), password, cancelledFlag);
	}

	public Vault unlock(UnlockToken token, CharSequence password, Flag cancelledFlag) throws BackendException {
		UnlockTokenImpl impl = (UnlockTokenImpl) token;
		Cryptor cryptor = cryptorFor(impl.getKeyFile(), password);

		if (cancelledFlag.get()) {
			throw new CancellationException();
		}

		cryptoCloudContentRepositoryFactory.registerCryptor(impl.getVault(), cryptor);

		return aCopyOf(token.getVault()) //
				.withVersion(impl.getKeyFile().getVersion()) //
				.build();
	}

	public UnlockTokenImpl createUnlockToken(Vault vault) throws BackendException {
		CloudFolder vaultLocation = cloudContentRepository.resolve(vault.getCloud(), vault.getPath());
		return createUnlockToken(vault, vaultLocation);
	}

	private UnlockTokenImpl createUnlockToken(Vault vault, CloudFolder location) throws BackendException {
		byte[] keyFileData = readKeyFileData(location);
		UnlockTokenImpl unlockToken = new UnlockTokenImpl(vault, keyFileData);
		assertVaultVersionIsSupported(unlockToken.getKeyFile().getVersion());
		return unlockToken;
	}

	private Cryptor cryptorFor(KeyFile keyFile, CharSequence password) {
		return cryptorProvider.createFromKeyFile(keyFile, normalizePassword(password, keyFile.getVersion()), keyFile.getVersion());
	}

	private CloudFolder vaultLocation(Vault vault) throws BackendException {
		return cloudContentRepository.resolve(vault.getCloud(), vault.getPath());
	}

	public boolean isVaultPasswordValid(Vault vault, CharSequence password) throws BackendException {
		try {
			// create a cryptor, which checks the password, then destroy it immediately
			cryptorFor(createUnlockToken(vault).getKeyFile(), password).destroy();
			return true;
		} catch (InvalidPassphraseException e) {
			return false;
		}
	}

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

	private void writeKeyFile(CloudFolder location, KeyFile keyFile) throws BackendException {
		byte[] data = keyFile.serialize();
		cloudContentRepository.write(masterkeyFile(location), ByteArrayDataSource.from(data), NO_OP_PROGRESS_AWARE, false, data.length);
	}

	private byte[] readKeyFileData(CloudFolder location) throws BackendException {
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		cloudContentRepository.read(masterkeyFile(location), Optional.empty(), data, NO_OP_PROGRESS_AWARE);
		return data.toByteArray();
	}

	private CloudFile masterkeyFile(CloudFolder location) throws BackendException {
		return cloudContentRepository.file(location, MASTERKEY_FILE_NAME);
	}

	private CloudFile masterkeyBackupFile(CloudFolder location, byte[] data) throws BackendException {
		String fileName = MASTERKEY_FILE_NAME + BackupFileIdSuffixGenerator.generate(data) + MASTERKEY_BACKUP_FILE_EXT;
		return cloudContentRepository.file(location, fileName);
	}

	public void changePassword(Vault vault, String oldPassword, String newPassword) throws BackendException {
		CloudFolder vaultLocation = vaultLocation(vault);
		ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
		cloudContentRepository.read(masterkeyFile(vaultLocation), Optional.empty(), dataOutputStream, NO_OP_PROGRESS_AWARE);

		byte[] data = dataOutputStream.toByteArray();
		int vaultVersion = KeyFile.parse(data).getVersion();

		createBackupMasterKeyFile(data, vaultLocation);
		createNewMasterKeyFile(data, vaultVersion, oldPassword, newPassword, vaultLocation);
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

		public KeyFile getKeyFile() {
			return KeyFile.parse(keyFileData);
		}
	}

	private void createBackupMasterKeyFile(byte[] data, CloudFolder vaultLocation) throws BackendException {
		cloudContentRepository.write( //
				masterkeyBackupFile(vaultLocation, data), //
				ByteArrayDataSource.from(data), //
				NO_OP_PROGRESS_AWARE, //
				true, //
				data.length);
	}

	private void createNewMasterKeyFile(byte[] data, int vaultVersion, String oldPassword, String newPassword, CloudFolder vaultLocation) throws BackendException {
		byte[] newMasterKeyFile = Cryptors.changePassphrase(cryptorProvider, //
				data, //
				normalizePassword(oldPassword, vaultVersion), //
				normalizePassword(newPassword, vaultVersion));
		cloudContentRepository.write(masterkeyFile(vaultLocation), //
				ByteArrayDataSource.from(newMasterKeyFile), //
				NO_OP_PROGRESS_AWARE, //
				true, //
				newMasterKeyFile.length);
	}

	private CharSequence normalizePassword(CharSequence password, int vaultVersion) {
		if (vaultVersion >= VERSION_WITH_NORMALIZED_PASSWORDS) {
			return normalize(password, Normalizer.Form.NFC);
		} else {
			return password;
		}
	}

}
