package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.data.repository.CloudContentRepositoryFactory;
import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.Vault;
import org.cryptomator.domain.exception.MissingCryptorException;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.util.Optional;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Lazy;

import static java.lang.String.format;
import static org.cryptomator.domain.CloudType.CRYPTO;

@Singleton
public class CryptoCloudContentRepositoryFactory implements CloudContentRepositoryFactory {

	private final Lazy<CloudContentRepository> cloudContentRepository;
	private final Cryptors cryptors;
	private final Context context;

	@Inject
	public CryptoCloudContentRepositoryFactory(Lazy<CloudContentRepository> cloudContentRepository, Cryptors cryptors, Context context) {
		this.cloudContentRepository = cloudContentRepository;
		this.cryptors = cryptors;
		this.context = context;
	}

	@Override
	public boolean supports(Cloud cloud) {
		return cloud.type() == CRYPTO;
	}

	@Override
	public CloudContentRepository cloudContentRepositoryFor(Cloud cloud) {
		CryptoCloud cryptoCloud = (CryptoCloud) cloud;
		Vault vault = cryptoCloud.getVault();
		return new CryptoCloudContentRepository(context, cloudContentRepository.get(), cryptoCloud, cryptors.get(vault));
	}

	public void deregisterCryptor(Vault vault) {
		deregisterCryptor(vault, true);
	}

	public void deregisterCryptor(Vault vault, boolean assertPresent) {
		Optional<Cryptor> cryptor = cryptors.remove(vault);
		if (cryptor.isAbsent()) {
			if (assertPresent) {
				throw new IllegalStateException(format("No cryptor registered for vault %s", vault));
			}
		} else {
			cryptor.get().destroy();
		}
	}

	public boolean cryptorIsRegisteredFor(Vault vault) {
		try {
			assertCryptorRegisteredFor(vault);
			return true;
		} catch (MissingCryptorException e) {
			return false;
		}
	}

	public void assertCryptorRegisteredFor(Vault vault) throws MissingCryptorException {
		cryptors.get(vault).get();
	}

	void registerCryptor(Vault vault, Cryptor cryptor) {
		if (!cryptors.putIfAbsent(vault, cryptor)) {
			throw new IllegalStateException(format("Cryptor already registered for vault %s", vault));
		}
	}
}
