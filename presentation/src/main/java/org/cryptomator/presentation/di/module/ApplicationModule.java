package org.cryptomator.presentation.di.module;

import android.content.Context;

import org.cryptomator.presentation.CryptomatorApp;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ApplicationModule {

	private final CryptomatorApp application;

	public ApplicationModule(CryptomatorApp application) {
		this.application = application;
	}

	@Provides
	@Singleton
	Context provideApplicationContext() {
		return application;
	}
}
