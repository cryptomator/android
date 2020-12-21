package org.cryptomator.data.cloud.crypto;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class CryptorsModule {

	private final Cryptors cryptors;

	public CryptorsModule(Cryptors cryptors) {
		this.cryptors = cryptors;
	}

	@Singleton
	@Provides
	public Cryptors provideCryptors() {
		return cryptors;
	}

}
