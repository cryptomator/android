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
