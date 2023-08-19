package org.cryptomator.presentation.docprovider

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.di.component.ApplicationComponent
import org.cryptomator.util.file.MimeTypeMap
import org.cryptomator.util.file.MimeTypes

internal val appComponent: ApplicationComponent by lazy { (CryptomatorApp.applicationContext() as CryptomatorApp).component } //Needs to be initialized after onCreate has finished //TODO Verify
internal val contentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> by lazy { appComponent.cloudContentRepository() }
internal val mimeTypes: MimeTypes = MimeTypes(MimeTypeMap())

//TODO Move this if/Make it the standard somewhere else
internal fun safeResolve(cloud: Cloud, path: VaultPath): CloudFolder {
	require(cloud.type() == CloudType.CRYPTO)

	//In the CryptoCloud the root folder is an instance of CryptoFolder
	return if (path.isRoot) contentRepository.root(cloud) else contentRepository.resolve(cloud, "/${path.path}")
}