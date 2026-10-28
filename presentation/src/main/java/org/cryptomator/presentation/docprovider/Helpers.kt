package org.cryptomator.presentation.docprovider

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.data.documents.DocumentContentCache
import org.cryptomator.data.documents.ThumbnailGenerator
import org.cryptomator.data.documents.ThumbnailStore
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.MissingCryptorException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.di.component.ApplicationComponent
import org.cryptomator.presentation.licensing.LicenseEnforcer
import org.cryptomator.presentation.model.VaultModel
import java.io.File
import java.io.FileNotFoundException

internal val appComponent: ApplicationComponent by lazy { (CryptomatorApp.applicationContext() as CryptomatorApp).awaitComponent() }

internal fun documentUri(documentId: String): Uri {
	return DocumentsContract.buildDocumentUri(CryptomatorDocumentsProvider.AUTHORITY, documentId)
}

internal fun MatrixCursor.setNotificationUriForId(cr: ContentResolver, documentId: String): Uri {
	return documentUri(documentId).also { setNotificationUri(cr, it) }
}

/**
 * The claimed copy of [path] that an external editor keeps using after its vault locked, see
 * [DocumentContentCache.pinnedFile], or null if the vault is unlocked. Any other document of a locked vault requires an
 * unlock.
 */
internal fun claimPinnedFileIfLocked(path: VaultPath, contentCache: DocumentContentCache, authenticationRequired: (Vault) -> Exception): File? {
	return if (path.vault.isUnlocked) null else contentCache.pinnedFile(path) ?: throw authenticationRequired(path.vault)
}

/**
 * Runs [request] of an unlocked [vault]. If the vault is locked meanwhile, it fails like a request of a locked vault, with
 * the exception [authenticationRequired] makes, rather than with a [MissingCryptorException] the client can't receive.
 */
internal fun <T> whileUnlocked(vault: Vault, authenticationRequired: (Vault) -> Exception, request: () -> T): T {
	try {
		return request()
	} catch (e: MissingCryptorException) {
		throw authenticationRequired(vault)
	}
}

/**
 * Like in the app, writing needs a license or a running trial, or for a Hub vault a paid Hub license, which only the vault
 * as it was unlocked knows about, see [unlockedVault]. Asking doesn't record that the trial expired, so that the app still
 * tells the user, see [LicenseEnforcer.peekWriteAccessForVault].
 */
internal fun isWritable(vault: Vault, licenseEnforcer: LicenseEnforcer, unlockedVault: (Vault) -> Vault?): Boolean {
	return !vault.cloud.isReadOnly() && !vault.isReadOnly && licenseEnforcer.peekWriteAccessForVault(VaultModel(unlockedVault(vault) ?: vault))
}

/**
 * Checked by every change, since a client may ignore the flags that tell it what it may do.
 */
internal fun requireWritable(path: VaultPath, licenseEnforcer: LicenseEnforcer, unlockedVault: (Vault) -> Vault?): VaultPath {
	if (!isWritable(path.vault, licenseEnforcer, unlockedVault)) {
		throw UnsupportedOperationException("Vault ${path.vault.name} is read-only")
	}
	return path
}

/**
 * The thumbnail of the image [metadata] at [path]. One that hasn't been made yet is asked for without waiting for it.
 *
 * @throws FileNotFoundException if it hasn't been made yet
 */
internal fun storedThumbnail(path: VaultPath, metadata: DocumentMetadataEntity, store: ThumbnailStore, generator: ThumbnailGenerator): File {
	return store.thumbnail(metadata) ?: run {
		generator.request(listOf(path))
		throw FileNotFoundException("The thumbnail of ${metadata.vaultId}:${metadata.id} is being made")
	}
}

/**
 * Runs [block] with the path of [documentId], found with [resolve]. If [block] doesn't find the document because it was
 * moved meanwhile, e.g. because a folder above it was renamed for a name taken in the cloud right after the path was
 * looked up, it runs again where the document is then.
 */
internal fun <T> followingDocument(documentId: String, resolve: (String) -> VaultPath, block: (VaultPath) -> T): T {
	var path = resolve(documentId)
	while (true) {
		try {
			return block(path)
		} catch (e: Exception) {
			if (e !is FileNotFoundException && e !is NoSuchCloudFileException) {
				throw e
			}
			val current = try {
				resolve(documentId)
			} catch (gone: FileNotFoundException) {
				throw e
			}
			if (current == path) {
				throw e
			}
			path = current
		}
	}
}
