package org.cryptomator.data.cloud.local

import android.database.Cursor
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.util.Date

internal object LocalStorageAccessFrameworkNodeFactory {

	fun from(parent: LocalStorageAccessFolder, cursor: Cursor): LocalStorageAccessNode {
		return if (isFolder(cursor)) {
			folder(parent, cursor)
		} else {
			file(parent, cursor)
		}
	}

	private fun file(parent: LocalStorageAccessFolder, cursor: Cursor): LocalStorageAccessFile {
		return LocalStorageAccessFile( //
			parent,  //
			cursor.getString(0),  //
			getNodePath(parent, cursor.getString(0)),  //
			cursor.getLong(2),  //
			Date(cursor.getLong(3)),  //
			cursor.getString(4),  //
			getDocumentUri(parent, cursor.getString(4))
		)
	}

	private fun folder(parent: LocalStorageAccessFolder, cursor: Cursor): LocalStorageAccessFolder {
		return LocalStorageAccessFolder(
			parent,  //
			cursor.getString(0),  //
			getNodePath(parent, cursor.getString(0)),  //
			cursor.getString(4),  //
			getDocumentUri(parent, cursor.getString(4))
		)
	}

	@JvmStatic
	fun from(parent: LocalStorageAccessFolder, documentFile: DocumentFile): LocalStorageAccessNode {
		return if (isFolder(documentFile)) {
			folder(parent, documentFile)
		} else {
			file(parent, documentFile)
		}
	}

	fun folder(parent: LocalStorageAccessFolder, directory: DocumentFile): LocalStorageAccessFolder {
		return LocalStorageAccessFolder(
			parent,  //
			directory.name!!,  // FIXME
			getNodePath(parent, directory.name),  //
			DocumentsContract.getDocumentId(directory.uri),  //
			directory.uri.toString()
		)
	}

	fun file(parent: LocalStorageAccessFolder, documentFile: DocumentFile): LocalStorageAccessFile {
		return LocalStorageAccessFile( //
			parent,  //
			documentFile.name!!,  // FIXME
			getNodePath(parent, documentFile.name),  //
			documentFile.length(),  //
			Date(documentFile.lastModified()),  //
			DocumentsContract.getDocumentId(documentFile.uri),  //
			documentFile.uri.toString()
		)
	}

	fun file(parent: LocalStorageAccessFolder, name: String, size: Long?): LocalStorageAccessFile {
		return LocalStorageAccessFile( //
			parent,  //
			name,  //
			getNodePath(parent, name),  //
			size,  //
			null,  //
			null,  //
			null
		)
	}

	@JvmStatic
	fun file(parent: LocalStorageAccessFolder, name: String, path: String, size: Long?, documentId: String): LocalStorageAccessFile {
		return LocalStorageAccessFile(
			parent,  //
			name,  //
			path,  //
			size,  //
			null,  //
			documentId,  //
			getDocumentUri(parent, documentId)
		)
	}

	fun folder(parent: LocalStorageAccessFolder, name: String): LocalStorageAccessFolder {
		return LocalStorageAccessFolder(
			parent,  //
			name,  //
			getNodePath(parent, name),  //
			null,  //
			null
		)
	}

	@JvmStatic
	fun folder(parent: LocalStorageAccessFolder, name: String, documentId: String): LocalStorageAccessFolder {
		return LocalStorageAccessFolder(
			parent,  //
			name,  //
			getNodePath(parent, name),  //
			documentId,  //
			getDocumentUri(parent, documentId)
		)
	}

	private fun getDocumentUri(parent: LocalStorageAccessFolder, documentId: String): String {
		return DocumentsContract.buildDocumentUriUsingTree(parent.uri, documentId).toString()
	}

	private fun isFolder(file: DocumentFile): Boolean {
		return file.isDirectory
	}

	private fun isFolder(cursor: Cursor): Boolean {
		return cursor.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR
	}

	@JvmStatic
	fun getNodePath(parent: LocalStorageAccessFolder, name: String?): String {
		return parent.path + "/" + name
	}
}
