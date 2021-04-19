package org.cryptomator.data.cloud.crypto;

import android.content.Context;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.domain.CloudFolder;
import org.cryptomator.domain.repository.CloudContentRepository;
import org.cryptomator.util.Supplier;

public class CryptoImplVaultFormat8 extends CryptoImplVaultFormat7 {

	CryptoImplVaultFormat8(Context context, Supplier<Cryptor> cryptor, CloudContentRepository cloudContentRepository, CloudFolder storageLocation, DirIdCache dirIdCache, int shorteningThreshold) {
		super(context, cryptor, cloudContentRepository, storageLocation, dirIdCache, shorteningThreshold);
	}

}
