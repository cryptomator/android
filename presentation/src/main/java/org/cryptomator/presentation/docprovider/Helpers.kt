package org.cryptomator.presentation.docprovider

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import org.cryptomator.data.cloud.crypto.CryptoFolder
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.di.component.ApplicationComponent
import org.cryptomator.util.file.MimeTypeMap
import org.cryptomator.util.file.MimeTypes

internal val appComponent: ApplicationComponent by lazy { (CryptomatorApp.applicationContext() as CryptomatorApp).component } //Needs to be initialized after onCreate has finished //TODO Verify
internal val contentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> by lazy { appComponent.cloudContentRepository() }
internal val mimeTypes: MimeTypes = MimeTypes(MimeTypeMap())

internal fun resolveNode(cloud: Cloud, nodePath: VaultPath): CloudNode? {
	require(cloud.type() == CloudType.CRYPTO)

	//TODO IMPROVE IMPLEMENTATION
	if (nodePath.isRoot) {
		return contentRepository.root(cloud)
	}
	//TODO Nullability
	return contentRepository.list(safeResolve(cloud, nodePath.parent!!)).find { it.name == nodePath.name }
}

//TODO Move this if/Make it the standard somewhere else
internal fun safeResolve(cloud: Cloud, path: VaultPath): CloudFolder {
	require(cloud.type() == CloudType.CRYPTO)

	//In the CryptoCloud the root folder is an instance of CryptoFolder
	return if (path.isRoot) contentRepository.root(cloud) else contentRepository.resolve(cloud, "/${path.path}")
}

internal fun CryptoFolder.isEmpty(): Boolean {
	return contentRepository.list(this).isEmpty()
}

internal fun MatrixCursor.setNotificationUriForPath(cr: ContentResolver, vaultPath: VaultPath): Uri {
	return setNotificationUriForId(cr, vaultPath.documentId)
}

internal fun MatrixCursor.setNotificationUriForId(cr: ContentResolver, documentId: String): Uri {
	return DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY, documentId).also { setNotificationUri(cr, it) }
}