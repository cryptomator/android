package org.cryptomator.data.cloud.crypto

import android.content.Context
import org.cryptomator.cryptolib.api.Cryptor
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.repository.CloudContentRepository
import java.util.function.Supplier

class CryptoImplVaultFormat8 internal constructor(
	context: Context,
	cryptor: Supplier<Cryptor>,
	cloudContentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile>,
	storageLocation: CloudFolder,
	dirIdCache: DirIdCache,
	shorteningThreshold: Int
) : CryptoImplVaultFormat7(
	context, cryptor, cloudContentRepository, storageLocation, dirIdCache, shorteningThreshold
)
