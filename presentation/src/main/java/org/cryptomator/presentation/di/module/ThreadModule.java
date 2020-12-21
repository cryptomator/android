package org.cryptomator.presentation.di.module;

import org.cryptomator.data.executor.JobExecutor;
import org.cryptomator.domain.executor.PostExecutionThread;
import org.cryptomator.domain.executor.ThreadExecutor;
import org.cryptomator.presentation.UIThread;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ThreadModule {

	@Provides
	@Singleton
	ThreadExecutor provideThreadExecutor(JobExecutor jobExecutor) {
		return jobExecutor;
	}

	@Provides
	@Singleton
	PostExecutionThread providePostExecutionThread(UIThread uiThread) {
		return uiThread;
	}
}
