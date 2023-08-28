package org.cryptomator.presentation.docprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract.*
import android.provider.DocumentsProvider
import org.cryptomator.data.cloud.crypto.CryptoFile
import org.cryptomator.data.cloud.crypto.CryptoFolder
import org.cryptomator.data.cloud.crypto.CryptoSymlink
import org.cryptomator.data.cloud.crypto.RootCryptoFolder
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.EmptyDataSource
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.util.file.MimeType
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
	//...
)

private val LOG = Timber.named("CryptomatorDocumentsProvider")

class CryptomatorDocumentsProvider : DocumentsProvider() {

	override fun onCreate(): Boolean {
		LOG.v("onCreate")
		return true
	}

	override fun queryRoots(projection: Array<String>?): Cursor {
		LOG.v("queryRoots")
		//TODO Handle unsupported columns
		try {
			return queryRootsImpl(projection ?: SUPPORTED_ROOT_COLUMNS)
		} catch (e: BackendException) {
			//TODO Just throw?
			e.printStackTrace()
			return MatrixCursor(projection ?: SUPPORTED_ROOT_COLUMNS)
		}
	}

	@Throws(BackendException::class)
	private fun queryRootsImpl(actualProjection: Array<String>): Cursor {
		//TODO Use custom cursor impl.?
		val vaults = appComponent.vaultRepository().vaults().filter { it.isUnlocked }
		val result = MatrixCursor(actualProjection)

		//TODO Actually only include requested columns
		vaults.forEach { vault ->
			result.newRow().apply {
				add(Root.COLUMN_ROOT_ID, vault.id.toString())
				add(Root.COLUMN_SUMMARY, vault.name)
				add(Root.COLUMN_FLAGS, rootFlags(vault))
				add(Root.COLUMN_TITLE, context?.getString(R.string.app_name) ?: "Cryptomator") //TODO Use Vault name?
				add(Root.COLUMN_DOCUMENT_ID, VaultPath(vault).documentId)
				add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
			}
		}
		return result
	}

	private fun rootFlags(vault: Vault): Int {
		val view = appComponent.cloudRepository().decryptedViewOf(vault)
		val rootNode = contentRepository.root(view)
		require(rootNode is RootCryptoFolder)

		var flags = 0
		if (rootNode.isEmpty()) {
			flags = flags or Root.FLAG_EMPTY //TODO Test
		}
		if (!vault.cloud.requiresNetwork()) { //Ask the backing cloud, not the CryptoCloud
			flags = flags or Root.FLAG_LOCAL_ONLY
		}
		if (!vault.cloud.isReadOnly() && !vault.isReadOnly) {
			flags = flags or Root.FLAG_SUPPORTS_CREATE
		}
		flags = flags or Root.FLAG_SUPPORTS_IS_CHILD

		//TODO FLAG_SUPPORTS_RECENTS, FLAG_SUPPORTS_SEARCH,

		return flags
	}

	override fun isChildDocument(parentDocumentId: String?, documentId: String?): Boolean {
		LOG.v("isChildDocument($parentDocumentId, $documentId)")
		requireNotNull(parentDocumentId)
		requireNotNull(documentId)

		//TODO Verify this (Why is there a trailing slash in the parentDocId?)
		return VaultPath(documentId).isAnyChildOf(VaultPath(parentDocumentId))
	}

	override fun queryChildDocuments(parentDocumentId: String?, projection: Array<String>?, sortOrder: String?): Cursor {
		throw UnsupportedOperationException("Use queryChildDocuments(String, String[], Bundle) instead.")
	}

	override fun queryChildDocuments(parentDocumentId: String?, projection: Array<String>?, queryArgs: Bundle?): Cursor {
		LOG.v("queryChildDocuments($parentDocumentId)")
		requireNotNull(parentDocumentId)

		//TODO Handle unsupported columns
		try {
			return queryChildDocumentsImpl(VaultPath(parentDocumentId), projection ?: SUPPORTED_DOCUMENT_COLUMNS, queryArgs)
		} catch (e: BackendException) {
			//TODO Just throw?
			e.printStackTrace()
			return MatrixCursor(projection ?: SUPPORTED_DOCUMENT_COLUMNS)
		}
	}

	private fun queryChildDocumentsImpl(directoryPath: VaultPath, projection: Array<String>, queryArgs: Bundle?): Cursor {
		//TODO queryArgs; e.g. android:query-arg-sort-direction = 0, android:query-arg-sort-columns = [Ljava.lang.String;@e9858a9
		val view: Cloud = appComponent.cloudRepository().decryptedViewOf(directoryPath.vault)

		//TODO Verify what happens if not a dir? Remove "/"?
		//TODO Add extension functions/Move to VaultPath

		val cloudDirPath: CloudFolder = safeResolve(view, directoryPath)

		val entries = contentRepository.list(cloudDirPath)
		val cursor = MatrixCursor(projection, entries.size)
		cursor.setNotificationUriForPath(context!!.contentResolver, directoryPath)

		entries.forEach { entry ->
			val isDir = entry is CryptoFolder
			//TODO Actually only include requested columns
			cursor.newRow().apply {
				val childPath = VaultPath(directoryPath.vault, entry.path)

				add(Document.COLUMN_DOCUMENT_ID, childPath.documentId)
				add(Document.COLUMN_DISPLAY_NAME, entry.name)
				add(Document.COLUMN_MIME_TYPE, mimeType(entry))
				add(Document.COLUMN_FLAGS, documentFlags(childPath, isDir))
				add(Document.COLUMN_SIZE, fileSize(entry))
				add(Document.COLUMN_LAST_MODIFIED, 0) //TODO
			}
		}
		return cursor
	}

	private fun mimeType(entry: CloudNode): String {
		require(requireNotNull(entry.cloud?.type()) == CloudType.CRYPTO)

		return when (entry) {
			is CryptoFile -> (mimeTypes.fromFilename(entry.name) ?: MimeType.WILDCARD_MIME_TYPE).toString()
			is CryptoFolder -> Document.MIME_TYPE_DIR
			is CryptoSymlink -> mimeType(entry.cloudFile)
			else -> throw IllegalArgumentException("Entry needs to be a CryptoNode, was ${entry.javaClass.name}")
		}
	}

	private fun fileSize(node: CloudNode): Long? {
		require(requireNotNull(node.cloud?.type()) == CloudType.CRYPTO)

		return when (node) {
			is CryptoFile -> node.size
			is CryptoFolder -> null
			is CryptoSymlink -> fileSize(node.cloudFile)
			else -> throw IllegalArgumentException("Node needs to be a CryptoNode, was ${node.javaClass.name}")
		}
	}

	override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
		LOG.v("queryDocument($documentId)")
		requireNotNull(documentId)

		//TODO Handle unsupported columns
		val actualProjection = projection ?: SUPPORTED_DOCUMENT_COLUMNS
		try {
			return VaultPath(documentId).let {
				if (it.isRoot) queryVaultRoot(it, actualProjection) else queryVaultDocument(it, actualProjection)
			}
		} catch (e: BackendException) {
			//TODO Just throw?
			e.printStackTrace()
			return MatrixCursor(projection ?: SUPPORTED_DOCUMENT_COLUMNS)
		}
	}

	@Throws(BackendException::class)
	private fun queryVaultRoot(rootPath: VaultPath, actualProjection: Array<String>): Cursor {
		require(rootPath.isRoot)

		//TODO Actually only include requested columns
		return singleRowCursor(actualProjection) {
			add(Document.COLUMN_DOCUMENT_ID, rootPath.documentId)
			add(Document.COLUMN_DISPLAY_NAME, rootPath.name)
			add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
			add(Document.COLUMN_FLAGS, rootDocumentFlags(rootPath.vault))
			add(Document.COLUMN_SIZE, null) //TODO
			add(Document.COLUMN_LAST_MODIFIED, 0) //TODO
		}
	}

	private fun rootDocumentFlags(vault: Vault): Int {
		val view = appComponent.cloudRepository().decryptedViewOf(vault)
		val rootNode = contentRepository.root(view)
		require(rootNode is RootCryptoFolder)

		var flags = 0
		if (!vault.cloud.isReadOnly() && !vault.isReadOnly) {
			flags = flags or Document.FLAG_DIR_SUPPORTS_CREATE
		}

		//No:
		//Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE
		//Document.FLAG_DIR_PREFERS_LAST_MODIFIED
		//Document.FLAG_VIRTUAL_DOCUMENT
		//Document.FLAG_WEB_LINKABLE (Hub?)

		//TODO
		//Document.FLAG_DIR_PREFERS_GRID
		//Document.FLAG_SUPPORTS_METADATA
		//Document.FLAG_SUPPORTS_SETTINGS
		//Document.FLAG_SUPPORTS_THUMBNAIL

		//TODO Allow copying a root into another root/outside the vault?

		return flags
	}

	@Throws(BackendException::class)
	private fun queryVaultDocument(documentPath: VaultPath, actualProjection: Array<String>): Cursor {
		require(!documentPath.isRoot)

		val view = appComponent.cloudRepository().decryptedViewOf(documentPath.vault)
		require(requireNotNull(view.type()) == CloudType.CRYPTO)

		val cloudNode: CloudNode = resolveNode(view, documentPath)!!
		val isDir = cloudNode is CryptoFolder

		//TODO Actually only include requested columns
		return singleRowCursor(actualProjection) {
			add(Document.COLUMN_DOCUMENT_ID, documentPath.documentId)
			add(Document.COLUMN_DISPLAY_NAME, documentPath.name)
			add(Document.COLUMN_MIME_TYPE, mimeType(cloudNode))
			add(Document.COLUMN_FLAGS, documentFlags(documentPath, isDir))
			add(Document.COLUMN_SIZE, fileSize(cloudNode))
			add(Document.COLUMN_LAST_MODIFIED, 0) //TODO
		}
	}

	private fun documentFlags(path: VaultPath, isDir: Boolean): Int {
		val view = appComponent.cloudRepository().decryptedViewOf(path.vault)
		val rootNode = contentRepository.root(view)
		require(rootNode is RootCryptoFolder)
		val canWrite = !path.vault.cloud.isReadOnly() && !path.vault.isReadOnly

		var flags = 0
		if (canWrite) {
			flags = flags or (if (isDir) Document.FLAG_DIR_SUPPORTS_CREATE else /*TODO Document.FLAG_SUPPORTS_WRITE*/ 0)
		}

		//No:
		//Document.FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE
		//Document.FLAG_DIR_PREFERS_LAST_MODIFIED
		//Document.FLAG_VIRTUAL_DOCUMENT
		//Document.FLAG_WEB_LINKABLE (Hub?)

		//TODO
		//Document.FLAG_DIR_PREFERS_GRID
		//Document.FLAG_SUPPORTS_METADATA
		//Document.FLAG_SUPPORTS_SETTINGS
		//Document.FLAG_SUPPORTS_THUMBNAIL
		//Document.FLAG_PARTIAL

		return flags
	}

	override fun openDocument(documentId: String?, mode: String?, signal: CancellationSignal?): ParcelFileDescriptor {
		LOG.v("openDocument($documentId, $mode)")
		requireNotNull(documentId)
		requireNotNull(mode)

		//TODO Use signal
		//TODO Support other modes
		//TODO Use ParcelFileDescriptor.parseMode()
		if (mode.lowercase() != "r") {
			throw UnsupportedOperationException(mode)
		}
		try {
			return openDocumentRO(VaultPath(documentId))
		} catch (e: BackendException) {
			throw RuntimeException(e)
		}
	}

	private fun openDocumentRO(documentPath: VaultPath): ParcelFileDescriptor {
		val storageManager = context!!.getSystemService(StorageManager::class.java) //TODO Nullability
		return storageManager.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, ROProxyFileDescriptorCallback(documentPath), Handler(Looper.getMainLooper())) //TODO Handler/Looper
	}

	override fun createDocument(parentDocumentId: String?, mimeType: String?, displayName: String?): String {
		LOG.v("createDocument($parentDocumentId, $mimeType, $displayName)")
		requireNotNull(parentDocumentId)
		requireNotNull(mimeType) //TODO Verify
		requireNotNull(displayName) //TODO Verify

		val parent = VaultPath(parentDocumentId)
		val view = appComponent.cloudRepository().decryptedViewOf(parent.vault)

		val parentNode = resolveExistingFolder(view, parent)
		val path = resolveUnusedPath(view, parent, cleanName(displayName))
		if (mimeType == Document.MIME_TYPE_DIR) {
			contentRepository.create(contentRepository.folder(parentNode, path.name))
		} else {
			//TODO Verify ProgressAware
			contentRepository.write(contentRepository.file(parentNode, path.name, 0), EmptyDataSource, ProgressAware.NO_OP_PROGRESS_AWARE_UPLOAD, false, 0)
		}
		notify(parentDocumentId)
		return path.documentId
	}

	private fun resolveUnusedPath(cloud: Cloud, parent: VaultPath, name: String): VaultPath {
		require(cloud.type() == CloudType.CRYPTO)

		val path = parent.resolve(name)
		val pathNode = resolveNode(cloud, path)
		if (pathNode != null) {
			require(contentRepository.exists(pathNode)) //Sanity check //TODO Remove with better implementation of resolveNode
			return resolveUnusedPath(cloud, parent, "${name}_") //TODO Better (non-recursive) algorithm
		}
		return path
	}

	private fun resolveExistingFolder(cloud: Cloud, nodePath: VaultPath): CloudFolder {
		require(cloud.type() == CloudType.CRYPTO)

		val node = resolveNode(cloud, nodePath)
		requireNotNull(node)
		require(nodePath.isRoot || contentRepository.exists(node))
		require(node is CloudFolder) //TODO Fix this mess

		return node
	}

	private fun cleanName(name: String): String {
		return name //TODO Implement
	}

	//TODO Call on VaultList change, lock/unlock, etc.
	fun notify(path: VaultPath) {
		return notify(path.documentId)
	}

	fun notify(documentId: String) {
		LOG.v("notify(${documentId})")
		val uri = buildDocumentUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY, documentId) //TODO Security implications

		//TODO notify root with buildRootsUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY)
		context?.contentResolver?.notifyChange(uri, null) ?: LOG.v("Can't call notifyChange for $documentId") //TODO Flags
	}
}

private fun singleRowCursor(projection: Array<String>, init: MatrixCursor.RowBuilder.() -> Unit): MatrixCursor {
	val cursor = MatrixCursor(projection)
	cursor.newRow().apply(init)
	return cursor
}