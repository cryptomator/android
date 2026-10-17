package org.cryptomator.data.repository;

import org.cryptomator.data.documents.VaultDocumentRepositoryImpl;
import org.cryptomator.domain.repository.BoxRepository;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.HubRepository;
import org.cryptomator.domain.repository.SharepointRepository;
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.domain.repository.VaultDocumentRepository;
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
	public VaultDocumentRepository provideVaultDocumentRepository(VaultDocumentRepositoryImpl vaultDocumentRepository) {
		return vaultDocumentRepository;
	}

	@Singleton
	@Provides
	public HubRepository provideHubRepositoryRepository(HubRepositoryImpl hubRepository) {
		return hubRepository;
	}

	@Singleton
	@Provides
	public SharepointRepository provideSharepointRepository(SharepointRepositoryImpl sharepointRepository) {
		return sharepointRepository;
	}

	@Singleton
	@Provides
	public BoxRepository provideBoxRepository(BoxRepositoryImpl boxRepository) {
		return boxRepository;
	}

	@Singleton
	@Provides
	public UpdateCheckRepository provideBetaStatusRepository(UpdateCheckRepositoryImpl updateCheckRepository) {
		return updateCheckRepository;
	}

}
