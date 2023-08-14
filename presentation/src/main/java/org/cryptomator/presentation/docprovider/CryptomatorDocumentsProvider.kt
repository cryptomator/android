package org.cryptomator.presentation.docprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import org.cryptomator.domain.Vault
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.CryptomatorApp.Companion.applicationContext
import org.cryptomator.presentation.R

private val SUPPORTED_ROOT_COLUMNS: Array<String> = arrayOf(
	//Required
	DocumentsContract.Root.COLUMN_ROOT_ID,
	DocumentsContract.Root.COLUMN_ICON,
	DocumentsContract.Root.COLUMN_TITLE,
	DocumentsContract.Root.COLUMN_FLAGS,
	DocumentsContract.Root.COLUMN_DOCUMENT_ID,
	//Optional
	DocumentsContract.Root.COLUMN_SUMMARY,
)

private val SUPPORTED_DOCUMENT_COLUMNS: Array<String> = arrayOf(
	//Required
	DocumentsContract.Document.COLUMN_DOCUMENT_ID,
	DocumentsContract.Document.COLUMN_DISPLAY_NAME,
	DocumentsContract.Document.COLUMN_MIME_TYPE,
	DocumentsContract.Document.COLUMN_FLAGS,
	DocumentsContract.Document.COLUMN_SIZE,
	DocumentsContract.Document.COLUMN_LAST_MODIFIED,
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
			e.printStackTrace()
			return MatrixCursor(SUPPORTED_ROOT_COLUMNS) //The actual columns don't matter cause it's empty
		}
	}

	@Throws(BackendException::class)
	private fun queryRootsImpl(actualProjection: Array<String>): Cursor {
		//TODO Use custom cursor impl.?
		//TODO Only show unlocked vaults
		val vaults = (applicationContext() as CryptomatorApp).component.vaultRepository().vaults()
		val result = MatrixCursor(actualProjection)

		//TODO Actually only include requested columns
		vaults.forEach { vault ->
			result.newRow().apply {
				add(DocumentsContract.Root.COLUMN_ROOT_ID, vault.id.toString())
				add(DocumentsContract.Root.COLUMN_SUMMARY, vault.name)
				add(DocumentsContract.Root.COLUMN_FLAGS, rootFlags())
				add(DocumentsContract.Root.COLUMN_TITLE, context?.getString(R.string.app_name) ?: "Cryptomator") //TODO Use Vault name?
				add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocumentIdForPath(vault, null))
				add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
			}
		}
		return result
	}

	private fun rootFlags(): Int {
		//TODO E.g `DocumentsContract.Root.FLAG_SUPPORTS_RECENTS or DocumentsContract.Root.FLAG_SUPPORTS_SEARCH`
		return 0
	}

	override fun queryChildDocuments(parentDocumentId: String?, projection: Array<String>?, sortOrder: String?): Cursor {
		TODO("Not yet implemented")
	}

	override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
		TODO("Not yet implemented")
	}

	override fun openDocument(documentId: String?, mode: String?, signal: CancellationSignal?): ParcelFileDescriptor {
		TODO("Not yet implemented")
	}

	//TODO Call on VaultList change
	fun refresh() {
		val rootsUri: Uri = DocumentsContract.buildRootsUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY)
		context?.contentResolver?.notifyChange(rootsUri, null)
	}
}

private fun getPathForDocumentId(documentId: String): Pair<Vault?, String?> {
	//TODO Symlinks
	val elements = documentId.split('/', ignoreCase = false, limit = 2)
	val vault = vaultById(elements.first().let { it.toLongOrNull() ?: throw IllegalArgumentException("Illegal vaultId: $it") })
	return vault to elements.lastOrNull()
}

private fun getDocumentIdForPath(vault: Vault, path: String?): String {
	//TODO Symlinks
	return "${vault.id}/${path.orEmpty()}"
}

private fun vaultById(id: Long): Vault? {
	return (applicationContext() as CryptomatorApp).component.vaultRepository().vaults().find { it.id == id }
}