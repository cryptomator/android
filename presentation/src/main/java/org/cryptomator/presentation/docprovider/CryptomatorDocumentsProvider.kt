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
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder
import org.cryptomator.domain.CloudNode
import org.cryptomator.domain.CloudType
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.util.file.MimeType

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

class CryptomatorDocumentsProvider : DocumentsProvider() {

	override fun onCreate(): Boolean {
		return true
	}

	override fun queryRoots(projection: Array<String>?): Cursor {
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
				add(Root.COLUMN_FLAGS, rootFlags())
				add(Root.COLUMN_TITLE, context?.getString(R.string.app_name) ?: "Cryptomator") //TODO Use Vault name?
				add(Root.COLUMN_DOCUMENT_ID, VaultPath(vault).documentId)
				add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
			}
		}
		return result
	}

	private fun rootFlags(): Int {
		//TODO E.g `DocumentsContract.Root.FLAG_SUPPORTS_RECENTS or DocumentsContract.Root.FLAG_SUPPORTS_SEARCH`
		return 0
	}

	override fun queryChildDocuments(parentDocumentId: String?, projection: Array<String>?, sortOrder: String?): Cursor {
		throw UnsupportedOperationException("Use queryChildDocuments(String, String[], Bundle) instead.")
	}

	override fun queryChildDocuments(parentDocumentId: String?, projection: Array<String>?, queryArgs: Bundle?): Cursor {
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

		entries.forEach { entry ->
			//TODO Actually only include requested columns
			cursor.newRow().apply {
				val childPath = VaultPath(directoryPath.vault, entry.path)

				add(Document.COLUMN_DOCUMENT_ID, childPath.documentId)
				add(Document.COLUMN_DISPLAY_NAME, entry.name)
				add(Document.COLUMN_MIME_TYPE, mimeType(entry))
				add(Document.COLUMN_FLAGS, 0) //TODO Flags (rootFlags?)
				add(Document.COLUMN_SIZE, null) //TODO
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

	override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
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
			add(Document.COLUMN_FLAGS, 0) //TODO Flags (rootFlags?)
			add(Document.COLUMN_SIZE, null) //TODO
			add(Document.COLUMN_LAST_MODIFIED, 0) //TODO
		}
	}

	@Throws(BackendException::class)
	private fun queryVaultDocument(documentPath: VaultPath, actualProjection: Array<String>): Cursor {
		require(!documentPath.isRoot)

		val view = appComponent.cloudRepository().decryptedViewOf(documentPath.vault)
		val cloudNode: CloudNode = resolveNode(view, documentPath)

		//TODO Actually only include requested columns
		return singleRowCursor(actualProjection) {
			add(Document.COLUMN_DOCUMENT_ID, documentPath.documentId)
			add(Document.COLUMN_DISPLAY_NAME, documentPath.name)
			add(Document.COLUMN_MIME_TYPE, mimeType(cloudNode))
			add(Document.COLUMN_FLAGS, 0) //TODO Flags (rootFlags?)
			add(Document.COLUMN_SIZE, null) //TODO
			add(Document.COLUMN_LAST_MODIFIED, 0) //TODO
		}
	}

	override fun openDocument(documentId: String?, mode: String?, signal: CancellationSignal?): ParcelFileDescriptor {
		requireNotNull(documentId)
		requireNotNull(mode)

		//TODO Use signal
		//TODO Support other modes
		//TODO Use ParcelFileDescriptor.parseMode()
		if (mode.lowercase() != "r") {
			throw UnsupportedOperationException()
		}
		try {
			return openDocumentRO(VaultPath(documentId))
		} catch (e: BackendException) {
			throw RuntimeException(e)
		}
	}

	private fun openDocumentRO(documentPath: VaultPath): ParcelFileDescriptor {
		val storageManager = context!!.getSystemService(StorageManager::class.java) //TODO Nullability
		return storageManager.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_WRITE, ROProxyFileDescriptorCallback(documentPath), Handler(Looper.getMainLooper())) //TODO Handler/Looper
	}

	//TODO Call on VaultList change
	fun refresh() {
		val rootsUri: Uri = buildRootsUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY)
		context?.contentResolver?.notifyChange(rootsUri, null)
	}
}

private fun singleRowCursor(projection: Array<String>, init: MatrixCursor.RowBuilder.() -> Unit): MatrixCursor {
	val cursor = MatrixCursor(projection)
	cursor.newRow().apply(init)
	return cursor
}