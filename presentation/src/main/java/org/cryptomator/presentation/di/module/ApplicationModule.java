package org.cryptomator.presentation.di.module;

import android.content.Context;

import org.cryptomator.presentation.CryptomatorApp;
import org.cryptomator.presentation.service.ProductPricesCache;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import kotlin.Unit;

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

	@Provides
	@Singleton
	ProductPricesCache provideProductPricesCache() {
		return new ProductPricesCache(callback -> {
			application.queryProductDetails(callback);
			return Unit.INSTANCE;
		}, System::currentTimeMillis);
	}
}
