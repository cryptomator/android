package org.cryptomator.data.repository;

import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.domain.repository.VaultRepository;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class RepositoryModule {

	@Singleton
	@Provides
	public CloudRepository provideCloudRepository(CloudRepositoryImpl cloudRepository) {
		return cloudRepository;
	}

	@Singleton
	@Provides
	public VaultRepository provideVaultRepository(VaultRepositoryImpl vaultRepository) {
		return vaultRepository;
	}

	@Singleton
	@Provides
	public CloudContentRepository provideCloudContentRepository(DispatchingCloudContentRepository cloudContentRepository) {
		return cloudContentRepository;
	}

	@Singleton
	@Provides
	public UpdateCheckRepository provideBetaStatusRepository(UpdateCheckRepositoryImpl updateCheckRepository) {
		return updateCheckRepository;
	}

}
