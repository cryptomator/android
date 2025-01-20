package org.cryptomator.data.cloud.crypto;

import com.google.common.base.Optional;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.CloudFile;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.CloudNode;
import org.cryptomator.domain.UnverifiedVaultConfig;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.BackendException;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.usecases.ProgressAware;
import org.cryptomator.domain.usecases.cloud.Flag;
import org.cryptomator.domain.usecases.vault.UnlockToken;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.cryptomator.data.cloud.crypto.CryptoConstants.VAULT_FILE_NAME;
import static org.cryptomator.domain.Vault.aCopyOf;

@Singleton
public class CryptoCloudFactory {

	private final CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> cloudContentRepository;
	private final CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory;

	@Inject
	public CryptoCloudFactory(CloudContentRepository/*<Cloud, CloudNode, CloudFolder, CloudFile>*/ cloudContentRepository, //
			CryptoCloudContentRepositoryFactory cryptoCloudContentRepositoryFactory) {
		this.cloudContentRepository = cloudContentRepository;
		this.cryptoCloudContentRepositoryFactory = cryptoCloudContentRepositoryFactory;
	}

	public void create(CloudFolder location, CharSequence password) throws BackendException {
		masterkeyCryptoCloudProvider().create(location, password);
	}

	public Cloud decryptedViewOf(Vault vault) throws BackendException {
		return new CryptoCloud(aCopyOf(vault).build());
	}

	public Optional<UnverifiedVaultConfig> unverifiedVaultConfig(Vault vault) throws BackendException {
		CloudFolder vaultLocation = cloudContentRepository.resolve(vault.getCloud(), vault.getPath());
		String jwt = new String(readConfigFileData(vaultLocation), StandardCharsets.UTF_8);
		return Optional.of(VaultConfig.decode(jwt));
	}

	private byte[] readConfigFileData(CloudFolder location) throws BackendException {
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		CloudFile vaultFile = cloudContentRepository.file(location, VAULT_FILE_NAME);
		cloudContentRepository.read(vaultFile, null, data, ProgressAware.NO_OP_PROGRESS_AWARE_DOWNLOAD);
		return data.toByteArray();
	}

	public Vault unlock(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password, Flag cancelledFlag) throws BackendException {
		return cryptoCloudProvider(unverifiedVaultConfig).unlock(createUnlockToken(vault, unverifiedVaultConfig), unverifiedVaultConfig, password, cancelledFlag);
	}

	public Vault unlock(UnlockToken token, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password, Flag cancelledFlag) throws BackendException {
		return cryptoCloudProvider(unverifiedVaultConfig).unlock(token, unverifiedVaultConfig, password, cancelledFlag);
	}

	public Vault unlock(Vault vault, UnverifiedVaultConfig unverifiedVaultConfig, String vaultKeyJwe, String userKeyJwe, Flag cancelledFlag) throws BackendException {
		return cryptoCloudProvider(unverifiedVaultConfig).unlock(vault, unverifiedVaultConfig, vaultKeyJwe, userKeyJwe, cancelledFlag);
	}

	public UnlockToken createUnlockToken(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig) throws BackendException {
		return cryptoCloudProvider(unverifiedVaultConfig).createUnlockToken(vault, unverifiedVaultConfig);
	}

	public boolean isVaultPasswordValid(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, CharSequence password) throws BackendException {
		return cryptoCloudProvider(unverifiedVaultConfig).isVaultPasswordValid(vault, unverifiedVaultConfig, password);
	}

	public void lock(Vault vault) {
		cryptoCloudContentRepositoryFactory.deregisterCryptor(vault);
	}

	public void changePassword(Vault vault, Optional<UnverifiedVaultConfig> unverifiedVaultConfig, String oldPassword, String newPassword) throws BackendException {
		cryptoCloudProvider(unverifiedVaultConfig).changePassword(vault, unverifiedVaultConfig, oldPassword, newPassword);
	}

	private CryptoCloudProvider masterkeyCryptoCloudProvider() {
		return cryptoCloudProvider(Optional.absent());
	}

	private CryptoCloudProvider cryptoCloudProvider(UnverifiedVaultConfig unverifiedVaultConfigOptional) {
		return cryptoCloudProvider(Optional.of(unverifiedVaultConfigOptional));
	}

	private CryptoCloudProvider cryptoCloudProvider(Optional<UnverifiedVaultConfig> unverifiedVaultConfigOptional) {
		if (unverifiedVaultConfigOptional.isPresent()) {
			return switch (unverifiedVaultConfigOptional.get().keyLoadingStrategy()) {
				case MASTERKEY -> new MasterkeyCryptoCloudProvider(cloudContentRepository, cryptoCloudContentRepositoryFactory, secureRandom());
				case HUB -> new HubkeyCryptoCloudProvider(cryptoCloudContentRepositoryFactory, secureRandom());
			};
		} else {
			return new MasterkeyCryptoCloudProvider(cloudContentRepository, cryptoCloudContentRepositoryFactory, secureRandom());
		}
	}

	private SecureRandom secureRandom() {
		try {
			return SecureRandom.getInstanceStrong();
		} catch (NoSuchAlgorithmException e) {
			throw new FatalBackendException("A strong algorithm must exist in every Java platform.", e);
		}
	}
}
