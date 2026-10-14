package org.cryptomator.presentation.docprovider

import android.app.AuthenticationRequiredException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.*
import android.provider.DocumentsProvider
import androidx.room.InvalidationTracker
import org.cryptomator.data.db.entities.DocumentMetadataEntity
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.Intents
import org.cryptomator.presentation.intent.UnlockVaultIntent
import org.cryptomator.presentation.model.VaultModel
import org.cryptomator.presentation.presenter.ContextHolder
import org.cryptomator.presentation.util.FileNameValidator
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
)

private val LOG = Timber.named("CryptomatorDocumentsProvider")

class CryptomatorDocumentsProvider : DocumentsProvider() {

	private val scheduler by lazy { CloudTaskScheduler() }
	private val metadataCache by lazy { DocumentMetadataCache(appComponent.documentMetadataDao(), scheduler, ::notify) }
	private val contentCache by lazy { DocumentContentCache(appComponent.cachedFileDao(), metadataCache, scheduler, File(context!!.noBackupFilesDir, DocumentContentCache.DIRECTORY_NAME)) }
	private val closeHandler by lazy { Handler(HandlerThread("DocumentsProviderClose").apply { start() }.looper) }

	override fun onCreate(): Boolean {
		LOG.v("onCreate")
		instance = this
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
		val vaults = appComponent.vaultRepository().vaults()
		val result = MatrixCursor(actualProjection)

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
		contentCache.resumeUploads(directoryPath.vault)
		val children = metadataCache.children(directoryPath)
		val cursor = MatrixCursor(projection, children.documents.size)
		cursor.setNotificationUriForId(context!!.contentResolver, directoryDocumentId)
		if (children.isLoading) {
			cursor.extras = Bundle().apply { putBoolean(EXTRA_LOADING, true) }
		} else if (children.error != null) {
			cursor.extras = errorExtras(children.error)
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
		add(Document.COLUMN_FLAGS, documentFlags(vault, metadata.isFolder))
		add(Document.COLUMN_SIZE, metadata.size)
		add(Document.COLUMN_LAST_MODIFIED, metadata.lastModified)
	}

	override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
		LOG.v("queryDocument($documentId)")
		requireNotNull(documentId)

		val actualProjection = projection ?: SUPPORTED_DOCUMENT_COLUMNS
		try {
			return metadataCache.path(documentId).let {
				if (it.isRoot) queryVaultRoot(documentId, it, actualProjection) else queryVaultDocument(requireUnlocked(it), actualProjection)
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
	 * Instead of an error, Files shows a button for the user action of the thrown exception, which unlocks the vault.
	 */
	private fun requireUnlocked(path: VaultPath): VaultPath {
		if (!path.vault.isUnlocked) {
			throw AuthenticationRequiredException(IllegalStateException("${path.vault.name} is locked"), unlockVaultIntent(path.vault))
		}
		return path
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
		val path = unlockedPath(documentId)
		try {
			if (accessMode == ParcelFileDescriptor.MODE_READ_ONLY) {
				val localFile = contentCache.file(path, signal)
				try {
					return ParcelFileDescriptor.open(localFile, accessMode)
				} finally {
					contentCache.release(localFile)
				}
			}
			if (!isWritable(path.vault)) {
				throw UnsupportedOperationException("Vault ${path.vault.name} is read-only")
			}
			return openForWriting(documentId, path, accessMode, signal)
		} catch (e: Exception) {
			if (e is FileNotFoundException || (e is RuntimeException && e !is FatalBackendException)) {
				throw e
			}
			throw FileNotFoundException("Opening $documentId failed").apply { initCause(e) }
		}
	}

	/**
	 * The document can be renamed or moved while it is open, so it is looked up again when it is closed.
	 */
	private fun openForWriting(documentId: String, path: VaultPath, accessMode: Int, signal: CancellationSignal?): ParcelFileDescriptor {
		val truncate = (accessMode and ParcelFileDescriptor.MODE_TRUNCATE) != 0
		val localFile = contentCache.fileForWriting(path, keepContent = !truncate, signal)
		val lengthBefore = localFile.length()
		val lastModifiedBefore = localFile.lastModified()
		try {
			return ParcelFileDescriptor.open(localFile, accessMode, closeHandler) { e ->
				if (e != null) {
					LOG.e(e, "Writing $documentId failed")
				}
				try {
					val changed = truncate || localFile.length() != lengthBefore || localFile.lastModified() != lastModifiedBefore
					contentCache.closedForWriting(localFile, e, changed) { metadataCache.path(documentId) }
				} catch (e: Exception) {
					LOG.e(e, "Saving $documentId failed")
				}
			}
		} catch (e: Exception) {
			contentCache.release(localFile)
			throw e
		}
	}

	override fun createDocument(parentDocumentId: String?, mimeType: String?, displayName: String?): String {
		LOG.v("createDocument($parentDocumentId, $mimeType)")
		requireNotNull(parentDocumentId)
		requireNotNull(mimeType)
		requireNotNull(displayName)

		return metadataCache.create(unlockedPath(parentDocumentId), FileNameValidator.sanitize(displayName), mimeType == Document.MIME_TYPE_DIR)
	}

	override fun refresh(uri: Uri?, extras: Bundle?, cancellationSignal: CancellationSignal?): Boolean {
		LOG.v("refresh($uri)")
		requireNotNull(uri)

		metadataCache.refresh(unlockedPath(getDocumentId(uri)))
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

		metadataCache.delete(path).forEach(::revokeDocumentPermission)
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

	/**
	 * Also notifies the roots if [path] is the root of a vault, whose [Root.FLAG_EMPTY] may have changed.
	 */
	fun notify(path: VaultPath) {
		metadataCache.documentId(path)?.let { notify(it) }
		if (path.isRoot) {
			notifyRoots()
		}
	}

	private fun notifyRoots() {
		context?.contentResolver?.notifyChange(buildRootsUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY), null)
	}

	fun notify(documentId: String) {
		LOG.v("notify(${documentId})")
		val uri = buildDocumentUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY, documentId)
		context?.contentResolver?.notifyChange(uri, null) ?: LOG.v("Can't call notifyChange for $documentId")
	}

	companion object {

		@Volatile
		private var instance: CryptomatorDocumentsProvider? = null

		/**
		 * Deletes the decrypted copies that are too old or too many, and those of removed vaults.
		 */
		@JvmStatic
		fun evictCopies() {
			instance?.contentCache?.evict()
		}

		/**
		 * Shows or hides the vaults in the system file picker. Hiding them deletes the decrypted copies that have no
		 * pending upload.
		 */
		fun setEnabled(context: Context, enabled: Boolean) {
			if (!enabled) {
				instance?.contentCache?.evictAll()
			}
			val component = ComponentName(context, CryptomatorDocumentsProvider::class.java)
			val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DEFAULT else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
			if (context.packageManager.getComponentEnabledSetting(component) != state) {
				context.packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
			}
		}
	}
}

private fun singleRowCursor(projection: Array<String>, init: MatrixCursor.RowBuilder.() -> Unit): MatrixCursor {
	val cursor = MatrixCursor(projection)
	cursor.newRow().apply(init)
	return cursor
}
