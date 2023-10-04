package org.cryptomator.presentation.docprovider;

import android.content.Context;

import org.cryptomator.data.cloud.crypto.Cryptors;
import org.cryptomator.data.repository.RepositoryModule;
import org.cryptomator.domain.usecases.cloud.GetCloudListUseCase;

import java.util.function.Supplier;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;

@Singleton
@Component(modules = {DocumentsProviderModule.class, RepositoryModule.class})
public interface DocumentsProviderComponent {

	@Singleton
	Supplier<GetCloudListUseCase> supplierOfGetCloudListUseCase();

	@Component.Builder
	interface Builder {

		@BindsInstance
		Builder withContext(Context context);

		@BindsInstance
		Builder withCryptors(Cryptors cryptors);

		DocumentsProviderComponent build();

	}
}