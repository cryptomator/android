package org.cryptomator.presentation.di.module;

import android.content.Context;

import org.cryptomator.presentation.CryptomatorApp;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class ApplicationModule {

	@Provides
	@Singleton
	Context provideApplicationContext(CryptomatorApp application) {
		return application;
	}
}
