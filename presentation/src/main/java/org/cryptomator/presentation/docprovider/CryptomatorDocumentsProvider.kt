package org.cryptomator.presentation.docprovider

import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.*
import android.provider.DocumentsProvider
import androidx.room.InvalidationTracker
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.data.documents.DocumentMetadataCache
import org.cryptomator.domain.Vault
import org.cryptomator.domain.VaultPath
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.repository.VaultDocumentListener
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.ContextHolder
import org.cryptomator.presentation.util.FileNameValidator
import org.cryptomator.util.SharedPreferencesHandler
import org.cryptomator.util.named
import java.io.File
import java.io.FileNotFoundException
import timber.log.Timber

private val SUPPORTED_ROOT_COLUMNS: Array<String> = arrayOf(
	//Required
	Root.COLUMN_ROOT_ID,
	Root.COLUMN_ICON,
	Root.COLUMN_TITLE,
	Root.COLUMN_FLAGS,
	Root.COLUMN_DOCUMENT_ID,
	//Optional
	Root.COLUMN_SUMMARY,
)

private val SUPPORTED_DOCUMENT_COLUMNS: Array<String> = arrayOf(
	//Required
	Document.COLUMN_DOCUMENT_ID,
	Document.COLUMN_DISPLAY_NAME,
	Document.COLUMN_MIME_TYPE,
	Document.COLUMN_FLAGS,
	Document.COLUMN_SIZE,
	Document.COLUMN_LAST_MODIFIED,
	//Optional
	Document.COLUMN_SUMMARY,
)

private val LOG = Timber.named("CryptomatorDocumentsProvider")

class CryptomatorDocumentsProvider : DocumentsProvider() {

	private val metadataCache by lazy { appComponent.documentMetadataCache() }
	private val contentCache by lazy { appComponent.documentContentCache() }
	private val thumbnailStore by lazy { appComponent.thumbnailStore() }
	private val thumbnailGenerator by lazy { appComponent.thumbnailGenerator() }
	private val closeHandler by lazy { Handler(HandlerThread("DocumentsProviderClose").apply { start() }.looper) }

	override fun onCreate(): Boolean {
		LOG.v("onCreate")
		return true
	}

	/**
	 * Files caches the roots, so it is told when a vault is added, renamed or removed.
	 */
	private val vaultChangesObserved by lazy {
		appComponent.cryptomatorDatabase().invalidationTracker.addObserver(object : InvalidationTracker.Observer("VAULT_ENTITY") {
			override fun onInvalidated(tables: Set<String>) {
				notifyRoots()
			}
		})
	}

	override fun queryRoots(projection: Array<String>?): Cursor {
		LOG.v("queryRoots")
		vaultChangesObserved
		val actualProjection = projection ?: SUPPORTED_ROOT_COLUMNS
		try {
			return queryRootsImpl(actualProjection)
		} catch (e: BackendException) {
			return errorCursor(actualProjection, e)
		}
	}

	@Throws(BackendException::class)
	private fun queryRootsImpl(actualProjection: Array<String>): Cursor {
		val result = MatrixCursor(actualProjection)
		if (!SharedPreferencesHandler(context!!).documentsProvider()) {
			return result
		}
		val vaults = appComponent.vaultRepository().vaults()

		vaults.forEach { vault ->
			result.newRow().apply {
				add(Root.COLUMN_ROOT_ID, vault.id.toString())
				add(Root.COLUMN_SUMMARY, vault.name)
				add(Root.COLUMN_FLAGS, rootFlags(vault))
				add(Root.COLUMN_TITLE, context?.getString(R.string.app_name) ?: "Cryptomator")
				add(Root.COLUMN_DOCUMENT_ID, metadataCache.documentId(VaultPath(vault)))
				add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
			}
		}
		return result
	}

	private fun rootFlags(vault: Vault): Int {
		var flags = 0
		if (vault.isUnlocked && metadataCache.isKnownEmpty(VaultPath(vault))) {
			flags = flags or Root.FLAG_EMPTY
		}
		if (!vault.cloud.requiresNetwork()) { //Ask the backing cloud, not the CryptoCloud
			flags = flags or Root.FLAG_LOCAL_ONLY
		}
		if (isWritable(vault)) {
			flags = flags or Root.FLAG_SUPPORTS_CREATE
		}
		flags = flags or Root.FLAG_SUPPORTS_IS_CHILD

		return flags
	}

	override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
		LOG.v("isChildDocument($parentDocumentId, $documentId)")
		requireNotNull(parentDocumentId)
		requireNotNull(documentId)

		return metadataCache.path(documentId).isAnyChildOf(metadataCache.path(parentDocumentId))
	}

	override fun queryChildDocuments(parentDocumentId: String?, projection: Array<String>?, sortOrder: String?): Cursor {
		throw UnsupportedOperationException("Use queryChildDocuments(String, String[], Bundle) instead.")
	}

	override fun queryChildDocuments(parentDocumentId: String?, projection: Array<String>?, queryArgs: Bundle?): Cursor {
		LOG.v("queryChildDocuments($parentDocumentId)")
		requireNotNull(parentDocumentId)

		val actualProjection = projection ?: SUPPORTED_DOCUMENT_COLUMNS
		try {
			return queryChildDocumentsImpl(parentDocumentId, actualProjection)
		} catch (e: BackendException) {
			return errorCursor(actualProjection, e)
		}
	}

	private fun queryChildDocumentsImpl(directoryDocumentId: String, projection: Array<String>): Cursor {
		val directoryPath = unlockedPath(directoryDocumentId)
		contentCache.resumeUploadsOnce(directoryPath.vault)
		val children = metadataCache.children(directoryPath)
		val cursor = MatrixCursor(projection, children.documents.size)
		cursor.setNotificationUriForId(context!!.contentResolver, directoryDocumentId)
		val error = children.error
		val extras = if (children.isLoading) {
			Bundle().apply { putBoolean(EXTRA_LOADING, true) }
		} else if (error != null) {
			errorExtras(error)
		} else {
			Bundle()
		}
		// Files shows no summary of a document, but this message at the top of the folder, like the error badge of iOS
		if (children.documents.any { metadataCache.uploadFailure(it) != null }) {
			extras.putString(EXTRA_INFO, context!!.getString(R.string.documents_provider_upload_failed_in_folder))
		}
		if (!extras.isEmpty) {
			cursor.extras = extras
		}

		children.documents.forEach { metadata ->
			cursor.newRow().addDocument(directoryPath.vault, metadata)
		}
		return cursor
	}

	private fun MatrixCursor.RowBuilder.addDocument(vault: Vault, metadata: DocumentMetadataEntity) {
		add(Document.COLUMN_DOCUMENT_ID, metadataCache.documentId(metadata))
		add(Document.COLUMN_DISPLAY_NAME, metadata.name)
		add(Document.COLUMN_MIME_TYPE, metadata.mimeType)
		add(Document.COLUMN_FLAGS, documentFlags(vault, metadata.isFolder) or thumbnailFlag(metadata))
		add(Document.COLUMN_SIZE, metadata.size)
		add(Document.COLUMN_LAST_MODIFIED, metadata.lastModified)
		// like the error badge of iOS; a refresh retries the uploads, see refresh()
		add(Document.COLUMN_SUMMARY, metadataCache.uploadFailure(metadata)?.let { context?.getString(R.string.documents_provider_upload_failed) })
	}

	override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
		LOG.v("queryDocument($documentId)")
		requireNotNull(documentId)

		val actualProjection = projection ?: SUPPORTED_DOCUMENT_COLUMNS
		try {
			return metadataCache.path(documentId).let {
				when {
					it.isRoot -> queryVaultRoot(documentId, it, actualProjection)
					!it.vault.isUnlocked && contentCache.isPinned(it) -> queryPinnedDocument(it, actualProjection)
					else -> queryVaultDocument(requireUnlocked(it), actualProjection)
				}
			}
		} catch (e: BackendException) {
			return errorCursor(actualProjection, e)
		}
	}

	private fun queryVaultRoot(documentId: String, rootPath: VaultPath, actualProjection: Array<String>): Cursor {
		require(rootPath.isRoot)

		return singleRowCursor(actualProjection) {
			add(Document.COLUMN_DOCUMENT_ID, documentId)
			add(Document.COLUMN_DISPLAY_NAME, rootPath.name)
			add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
			add(Document.COLUMN_FLAGS, rootDocumentFlags(rootPath.vault))
			add(Document.COLUMN_SIZE, null)
			add(Document.COLUMN_LAST_MODIFIED, null)
		}
	}

	private fun rootDocumentFlags(vault: Vault): Int {
		var flags = 0
		if (isWritable(vault)) {
			flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
		}
		return flags
	}

	private fun unlockedPath(documentId: String): VaultPath {
		return requireUnlocked(metadataCache.path(documentId))
	}

	/**
	 * Instead of an error, Files shows a button for the user action of the thrown exception, which unlocks the vault. A
	 * vault that is unlocked counts as used, so that it doesn't lock while another app uses it.
	 */
	private fun requireUnlocked(path: VaultPath): VaultPath {
		if (!path.vault.isUnlocked) {
			throw authenticationRequired(path.vault)
		}
		vaultsUsed()
		return path
	}

	private fun vaultsUsed() {
		(context?.applicationContext as? CryptomatorApp)?.vaultsUsed()
	}

	private fun authenticationRequired(vault: Vault): AuthenticationRequiredException {
		return AuthenticationRequiredException(IllegalStateException("${vault.name} is locked"), unlockVaultIntent(vault))
	}

	private fun unlockVaultIntent(vault: Vault): PendingIntent {
		val context = context!!
		val intent = Intents.unlockVaultIntent() //
			.withVaultModel(VaultModel(vault)) //
			.withVaultAction(UnlockVaultIntent.VaultAction.UNLOCK) //
			.build(object : ContextHolder {
				override fun context(): Context = context
			})
		return PendingIntent.getActivity(context, vault.id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
	}

	private fun isWritable(vault: Vault): Boolean {
		return !vault.cloud.isReadOnly() && !vault.isReadOnly
	}

	@Throws(BackendException::class)
	private fun queryVaultDocument(documentPath: VaultPath, actualProjection: Array<String>): Cursor {
		require(!documentPath.isRoot)

		val metadata = metadataCache.requireDocument(documentPath)

		return singleRowCursor(actualProjection) {
			addDocument(documentPath.vault, metadata)
		}
	}

	/**
	 * An external editor can keep using a document it opened from the app after the vault locks, see [openDocument], so
	 * it can still look it up. It can read and write the document, but renaming, deleting and anything else require an
	 * unlock.
	 */
	private fun queryPinnedDocument(documentPath: VaultPath, actualProjection: Array<String>): Cursor {
		val metadata = metadataCache.cachedDocument(documentPath) ?: throw FileNotFoundException("Unknown document: ${metadataCache.logId(documentPath)}")
		return singleRowCursor(actualProjection) {
			addDocument(documentPath.vault, metadata)
			add(Document.COLUMN_FLAGS, if (isWritable(documentPath.vault)) Document.FLAG_SUPPORTS_WRITE else 0)
		}
	}

	private fun documentFlags(vault: Vault, isDir: Boolean): Int {
		var flags = 0
		if (isWritable(vault)) {
			flags = flags or (if (isDir) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE)
			flags = flags or Document.FLAG_SUPPORTS_DELETE or //
					Document.FLAG_SUPPORTS_RENAME or //
					Document.FLAG_SUPPORTS_MOVE or //
					Document.FLAG_SUPPORTS_REMOVE
		}
		return flags
	}

	private fun thumbnailFlag(metadata: DocumentMetadataEntity): Int {
		return if (metadata.isImage) Document.FLAG_SUPPORTS_THUMBNAIL else 0
	}

	/**
	 * A thumbnail that hasn't been made yet is made in the background. Files shows the icon meanwhile and asks again once
	 * the thumbnail's folder is notified.
	 */
	override fun openDocumentThumbnail(documentId: String?, sizeHint: Point?, signal: CancellationSignal?): AssetFileDescriptor {
		LOG.v("openDocumentThumbnail($documentId)")
		requireNotNull(documentId)

		val path = unlockedPath(documentId)
		val metadata = metadataCache.cachedDocument(path) ?: throw FileNotFoundException("Unknown document: $documentId")
		val thumbnail = storedThumbnail(path, metadata, thumbnailStore, thumbnailGenerator)
		return AssetFileDescriptor(ParcelFileDescriptor.open(thumbnail, ParcelFileDescriptor.MODE_READ_ONLY), 0, thumbnail.length())
	}

	private fun errorCursor(projection: Array<String>, e: BackendException): Cursor {
		LOG.e(e, "Query failed")
		return MatrixCursor(projection).apply {
			extras = errorExtras(e)
		}
	}

	private fun errorExtras(e: Throwable): Bundle {
		return Bundle().apply { putString(EXTRA_ERROR, e.message ?: e.javaClass.simpleName) }
	}

	override fun openDocument(documentId: String?, mode: String?, signal: CancellationSignal?): ParcelFileDescriptor {
		LOG.v("openDocument($documentId, $mode)")
		requireNotNull(documentId)
		requireNotNull(mode)

		val accessMode = ParcelFileDescriptor.parseMode(mode)
		val path = metadataCache.path(documentId)
		val pinnedFile = claimPinnedFileIfLocked(path, contentCache, ::authenticationRequired)
		if (pinnedFile == null) {
			vaultsUsed()
		}
		try {
			if (accessMode == ParcelFileDescriptor.MODE_READ_ONLY) {
				val localFile = pinnedFile ?: contentCache.file(path, signal)
				try {
					// kept in use until the reader closes the file, so that a writer gets a copy of its own meanwhile, see
					// DocumentContentCache.fileForWriting. A reader that detached the file descriptor, e.g. for native code,
					// closes it unnoticed, so its copy stays in use until the process ends.
					return ParcelFileDescriptor.open(localFile, accessMode, closeHandler) { e ->
						if (e !is ParcelFileDescriptor.FileDescriptorDetachedException) {
							contentCache.release(localFile)
						}
					}
				} catch (e: Exception) {
					contentCache.release(localFile)
					throw e
				}
			}
			try {
				if (!isWritable(path.vault)) {
					throw UnsupportedOperationException("Vault ${path.vault.name} is read-only")
				}
				return openForWriting(documentId, path, accessMode, signal, pinnedFile)
			} finally {
				pinnedFile?.let(contentCache::release)
			}
		} catch (e: Exception) {
			if (e is FileNotFoundException || (e is RuntimeException && e !is FatalBackendException)) {
				throw e
			}
			throw FileNotFoundException("Opening $documentId failed").apply { initCause(e) }
		}
	}

	/**
	 * The document can be renamed or moved while it is open, so it is looked up again when it is closed. [pinnedFile] is
	 * the copy [openDocument] claimed because the vault is locked, which doesn't make it a copy someone else uses.
	 */
	private fun openForWriting(documentId: String, path: VaultPath, accessMode: Int, signal: CancellationSignal?, pinnedFile: File?): ParcelFileDescriptor {
		val truncate = (accessMode and ParcelFileDescriptor.MODE_TRUNCATE) != 0
		val localFile = contentCache.fileForWriting(path, keepContent = !truncate, signal, claimedByCaller = pinnedFile)
		val lengthBefore = localFile.length()
		// dated back by fileForWriting, so that any write changes it
		val lastModifiedBefore = localFile.lastModified()
		try {
			return ParcelFileDescriptor.open(localFile, accessMode, closeHandler) { e ->
				if (e is ParcelFileDescriptor.FileDescriptorDetachedException) {
					LOG.i("The writer of $documentId detached the file descriptor, saving once it closes the file")
				} else if (e != null) {
					LOG.e(e, "Writing $documentId failed")
				}
				try {
					val changed = truncate || localFile.length() != lengthBefore || localFile.lastModified() != lastModifiedBefore
					contentCache.closedForWriting(localFile, e, changed) { metadataCache.path(documentId, includeHidden = true) }
				} catch (e: Exception) {
					LOG.e(e, "Saving $documentId failed")
				}
			}
		} catch (e: Exception) {
			// the writer never got the file, also not a copy of its own
			contentCache.closedForWriting(localFile, null, changed = false) { path }
			throw e
		}
	}

	override fun createDocument(parentDocumentId: String?, mimeType: String?, displayName: String?): String {
		LOG.v("createDocument($parentDocumentId, $mimeType)")
		requireNotNull(parentDocumentId)
		requireNotNull(mimeType)
		requireNotNull(displayName)

		return metadataCache.create(unlockedPath(parentDocumentId), FileNameValidator.sanitize(displayName), mimeType == Document.MIME_TYPE_DIR).documentId
	}

	/**
	 * A refresh is asked for by the user, so unlike a listing, which Files also makes after each failed upload, it retries
	 * the uploads every time.
	 */
	override fun refresh(uri: Uri?, extras: Bundle?, cancellationSignal: CancellationSignal?): Boolean {
		LOG.v("refresh($uri)")
		requireNotNull(uri)

		val path = unlockedPath(getDocumentId(uri))
		contentCache.resumeUploads(path.vault)
		metadataCache.refresh(path)
		return true
	}

	override fun removeDocument(documentId: String?, parentDocumentId: String?) {
		LOG.v("removeDocument($documentId, $parentDocumentId)")
		requireNotNull(documentId)
		requireNotNull(parentDocumentId)
		require(unlockedPath(documentId).parent == metadataCache.path(parentDocumentId)) { "$parentDocumentId is not the parent of $documentId" }

		//A document has only one parent in a vault, so removing it from its parent deletes it
		deleteDocument(documentId)
		revokeDocumentPermission(documentId)
	}

	override fun deleteDocument(documentId: String?) {
		LOG.v("deleteDocument($documentId)")
		requireNotNull(documentId)

		val path = unlockedPath(documentId)
		require(!path.isRoot)

		contentCache.delete(path).descendantIds.forEach(::revokeDocumentPermission)
	}

	override fun moveDocument(sourceDocumentId: String?, sourceParentDocumentId: String?, targetParentDocumentId: String?): String {
		LOG.v("moveDocument($sourceDocumentId, $sourceParentDocumentId, $targetParentDocumentId)")
		requireNotNull(sourceDocumentId)
		requireNotNull(sourceParentDocumentId)
		requireNotNull(targetParentDocumentId)

		val sourcePath = unlockedPath(sourceDocumentId)
		require(sourcePath.parent == metadataCache.path(sourceParentDocumentId)) { "$sourceParentDocumentId is not the parent of $sourceDocumentId" }
		return moveDocumentImpl(sourceDocumentId, sourcePath, metadataCache.path(targetParentDocumentId))
	}

	private fun moveDocumentImpl(sourceDocumentId: String, sourcePath: VaultPath, newParentPath: VaultPath): String {
		require(!sourcePath.isRoot)
		val oldParentPath = sourcePath.parent!!
		require(sourcePath.vault.id == newParentPath.vault.id)

		if (oldParentPath == newParentPath) {
			LOG.v("moveDocument: $sourceDocumentId quietly not moved; the target is already its parent")
			return sourceDocumentId
		}
		require(sourcePath != newParentPath && !newParentPath.isAnyChildOf(sourcePath)) { "Can't move $sourceDocumentId into itself" }

		metadataCache.move(sourcePath, newParentPath, sourcePath.name)
		return sourceDocumentId
	}

	override fun renameDocument(documentId: String?, displayName: String?): String? {
		LOG.v("renameDocument($documentId)")
		requireNotNull(documentId)
		requireNotNull(displayName)

		val currentPath = unlockedPath(documentId)
		require(!currentPath.isRoot)
		val newName = FileNameValidator.sanitize(displayName)
		if (currentPath.name == newName) {
			LOG.v("renameDocument: $documentId quietly not renamed; requested name equals current name")
			return null
		}

		metadataCache.move(currentPath, currentPath.parent!!, newName)
		return null //The documentId stays the same
	}

	private fun notifyRoots() {
		context?.contentResolver?.let { notifyRoots(it) }
	}

	/**
	 * Tells Files about the changes to the cached vault documents, whether the provider or the app made them. It doesn't
	 * need the provider instance, so it can be registered before the provider is created.
	 */
	class ChangeNotifier(private val contentResolver: ContentResolver, private val metadataCache: () -> DocumentMetadataCache) : VaultDocumentListener {

		/**
		 * Also notifies the roots if [folder] is the root of a vault, whose [Root.FLAG_EMPTY] may have changed.
		 */
		override fun onChanged(folder: VaultPath) {
			metadataCache().documentId(folder)?.let { documentId ->
				LOG.v("notify($documentId)")
				contentResolver.notifyChange(documentUri(documentId), null)
			}
			if (folder.isRoot) {
				notifyRoots(contentResolver)
			}
		}
	}

	companion object {

		/**
		 * The authority of the provider, derived from the application ID like the FileProvider's, so that the flavors can be
		 * installed side by side.
		 */
		const val AUTHORITY = BuildConfig.APPLICATION_ID + ".documents"

		private fun notifyRoots(contentResolver: ContentResolver) {
			contentResolver.notifyChange(buildRootsUri(AUTHORITY), null)
		}

		/**
		 * Deletes the decrypted copies of removed vaults and the outdated thumbnails.
		 */
		@JvmStatic
		fun evictCopies() {
			appComponent.documentContentCache().evict()
		}

		/**
		 * Lets the next listing of each unlocked vault resume its unfinished uploads, e.g. those that failed because the
		 * vault was locked. Called whenever a vault was locked or unlocked.
		 */
		@JvmStatic
		fun resumeUploadsOnNextListing() {
			appComponent.documentContentCache().forgetResumedUploads()
		}

		/**
		 * Whether uploads or downloads someone waits for are running, which a graceful automatic lock waits for.
		 */
		@JvmStatic
		fun hasTransfers(): Boolean {
			return appComponent.documentContentCache().hasTransfers()
		}

		/**
		 * Shows or hides the vaults in the system file picker once the setting has been saved. Hiding them revokes the
		 * access other apps were given to documents and trees, including those the cache no longer knows. The app itself
		 * keeps opening files through the provider.
		 */
		fun setShown(context: Context, shown: Boolean) {
			if (!shown) {
				listOf("document", "tree").forEach { path ->
					val prefix = Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY).appendPath(path).build()
					context.revokeUriPermission(prefix, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
				}
			}
			// the preference is saved once its change listener has returned
			Handler(Looper.getMainLooper()).post { notifyRoots(context.contentResolver) }
		}
	}
}

private fun singleRowCursor(projection: Array<String>, init: MatrixCursor.RowBuilder.() -> Unit): MatrixCursor {
	val cursor = MatrixCursor(projection)
	cursor.newRow().apply(init)
	return cursor
}
