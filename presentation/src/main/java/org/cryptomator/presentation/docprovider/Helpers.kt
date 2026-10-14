package org.cryptomator.presentation.docprovider

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.Vault
import org.cryptomator.domain.repository.CloudContentRepository
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.di.component.ApplicationComponent
import org.cryptomator.util.file.MimeTypeMap
import org.cryptomator.util.file.MimeTypes
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

internal val appComponent: ApplicationComponent by lazy { (CryptomatorApp.applicationContext() as CryptomatorApp).awaitComponent() }
internal val contentRepository: CloudContentRepository<Cloud, CloudNode, CloudFolder, CloudFile> by lazy { appComponent.cloudContentRepository() }
internal val mimeTypes: MimeTypes by lazy { MimeTypes(MimeTypeMap()) }

internal fun decryptedView(vault: Vault): Cloud {
	return appComponent.cloudRepository().decryptedViewOf(vault)
}

/**
 * Waits for a cloud request and rethrows what made it fail. Cloud requests never run on the binder thread, because it
 * takes over the StrictMode policy of the calling app. They would fail with a NetworkOnMainThreadException whenever an
 * app calls the provider from its main thread. If [signal] is canceled, waiting ends right away, but the request runs on
 * so that it keeps its place in the order of requests.
 */
internal fun <T> CompletableFuture<T>.await(signal: CancellationSignal? = null): T {
	val waiting = if (signal == null) this else CompletableFuture<T>().also { waiting ->
		whenComplete { result, e -> if (e != null) waiting.completeExceptionally(e) else waiting.complete(result) }
		signal.setOnCancelListener { waiting.cancel(false) }
	}
	try {
		return waiting.get()
	} catch (e: ExecutionException) {
		throw e.cause ?: e
	} catch (e: CancellationException) {
		signal?.throwIfCanceled()
		throw e
	} finally {
		signal?.setOnCancelListener(null)
	}
}

/**
 * Modification dates are compared to the second because not every cloud reports milliseconds.
 */
internal fun isSameSecond(lastModified: Long?, otherLastModified: Long?): Boolean {
	return lastModified != null && otherLastModified != null && TimeUnit.MILLISECONDS.toSeconds(lastModified) == TimeUnit.MILLISECONDS.toSeconds(otherLastModified)
}

/**
 * The name the iOS app gives the copy of a file that changed both locally and in the cloud, e.g. `notes (1A2B3).txt`.
 */
internal fun conflictCopyName(name: String, suffix: String): String {
	val extensionStart = name.lastIndexOf('.')
	return if (extensionStart > 0) "${name.substring(0, extensionStart)} ($suffix)${name.substring(extensionStart)}" else "$name ($suffix)"
}

internal fun safeResolve(folder: VaultPath): CloudFolder {
	val cloud = decryptedView(folder.vault)
	//resolve() can't return the root: "/" resolves to a child with an empty name
	return if (folder.isRoot) contentRepository.root(cloud) else contentRepository.resolve(cloud, "/${folder.path}")
}

internal fun parentFolder(path: VaultPath): CloudFolder {
	return safeResolve(requireNotNull(path.parent))
}

internal fun MatrixCursor.setNotificationUriForId(cr: ContentResolver, documentId: String): Uri {
	return DocumentsContract.buildDocumentUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY, documentId).also { setNotificationUri(cr, it) }
}
