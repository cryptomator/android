package org.cryptomator.presentation.docprovider;

import static org.cryptomator.presentation.docprovider.DocumentsProviderModule.InternalPerViewComponent;

import org.cryptomator.data.executor.JobExecutor;
import org.cryptomator.domain.di.PerView;
import org.cryptomator.domain.executor.PostExecutionThread;
import org.cryptomator.domain.executor.ThreadExecutor;
import org.cryptomator.domain.usecases.cloud.GetCloudListUseCase;

import java.util.function.Supplier;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.Subcomponent;
import io.reactivex.Scheduler;
import io.reactivex.internal.schedulers.ExecutorScheduler;

@Module(subcomponents = {InternalPerViewComponent.class})
public class DocumentsProviderModule {

	@Provides
	@Singleton
	ThreadExecutor provideThreadExecutor(JobExecutor jobExecutor) {
		return jobExecutor;
	}

	@Provides
	@Singleton
	PostExecutionThread providePostExecutionThread(ThreadExecutor executor) {
		//TODO Improve this
		Scheduler scheduler = new ExecutorScheduler(executor, true); //TODO Verify boolean
		return () -> scheduler;
	}

	@Provides
	@Singleton
	Supplier<GetCloudListUseCase> provideSupplierOfGetCloudListUseCase(InternalPerViewComponent.Factory factory) {
		return () -> factory.build().instaceOfGetCloudListUseCase();
	}

	@Subcomponent
	@PerView
	interface InternalPerViewComponent {

		GetCloudListUseCase instaceOfGetCloudListUseCase();

		@Subcomponent.Factory
		interface Factory {

			InternalPerViewComponent build();

		}
	}
}
