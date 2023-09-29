package org.cryptomator.presentation.di.component;

import android.content.Context;

import org.cryptomator.data.cloud.crypto.Cryptors;
import org.cryptomator.data.repository.RepositoryModule;
import org.cryptomator.data.util.NetworkConnectionCheck;
import org.cryptomator.domain.executor.PostExecutionThread;
import org.cryptomator.domain.executor.ThreadExecutor;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.domain.repository.CloudRepository;
import org.cryptomator.domain.repository.UpdateCheckRepository;
import org.cryptomator.domain.repository.VaultRepository;
import org.cryptomator.presentation.CryptomatorApp;
import org.cryptomator.presentation.di.module.ApplicationModule;
import org.cryptomator.presentation.di.module.ThreadModule;
import org.cryptomator.presentation.util.ContentResolverUtil;
import org.cryptomator.presentation.util.FileUtil;

import javax.inject.Singleton;

import dagger.BindsInstance;
import dagger.Component;

@Singleton
@Component(modules = {ApplicationModule.class, ThreadModule.class, RepositoryModule.class})
public interface ApplicationComponent {

	Context context();

	ThreadExecutor threadExecutor();

	PostExecutionThread postExecutionThread();

	VaultRepository vaultRepository();

	CloudContentRepository cloudContentRepository();

	CloudRepository cloudRepository();

	UpdateCheckRepository updateCheckRepository();

	FileUtil fileUtil();

	ContentResolverUtil contentResolverUtil();

	NetworkConnectionCheck networkConnectionCheck();

	@Component.Builder
	interface Builder {

		@BindsInstance
		Builder withApp(CryptomatorApp app);

		@BindsInstance
		Builder withCryptors(Cryptors cryptors);

		ApplicationComponent build();
	}

}
