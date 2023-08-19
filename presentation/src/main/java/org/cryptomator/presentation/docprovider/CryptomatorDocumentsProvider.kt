package org.cryptomator.presentation.docprovider

import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.*
import android.provider.DocumentsProvider
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R

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
			e.printStackTrace()
			return MatrixCursor(SUPPORTED_ROOT_COLUMNS) //The actual columns don't matter cause it's empty
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
		val rootsUri: Uri = buildRootsUri(BuildConfig.DOCUMENTS_PROVIDER_AUTHORITY)
		context?.contentResolver?.notifyChange(rootsUri, null)
	}
}