package org.cryptomator.data.cloud.local.storageaccessframework;

import android.database.Cursor;
import android.os.Build;
import android.provider.DocumentsContract;

import org.cryptomator.util.Optional;

import java.util.Date;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class LocalStorageAccessFrameworkNodeFactory {

	public static LocalStorageAccessNode from(LocalStorageAccessFolder parent, Cursor cursor) {
		if (isFolder(cursor)) {
			return folder(parent, cursor);
		} else {
			return file(parent, cursor);
		}
	}

	private static LocalStorageAccessFile file(LocalStorageAccessFolder parent, Cursor cursor) {
		return new LocalStorageAccessFile( //
				parent, //
				cursor.getString(0), //
				getNodePath(parent, cursor.getString(0)), //
				Optional.of(cursor.getLong(2)), //
				Optional.of(new Date(cursor.getLong(3))), //
				cursor.getString(4), //
				getDocumentUri(parent, cursor.getString(4)));
	}

	private static LocalStorageAccessFolder folder(LocalStorageAccessFolder parent, Cursor cursor) {
		return new LocalStorageAccessFolder(parent, //
				cursor.getString(0), //
				getNodePath(parent, cursor.getString(0)), //
				cursor.getString(4), //
				getDocumentUri(parent, cursor.getString(4)));
	}

	public static LocalStorageAccessNode from(LocalStorageAccessFolder parent, DocumentFile documentFile) {
		if (isFolder(documentFile)) {
			return folder(parent, documentFile);
		} else {
			return file(parent, documentFile);
		}
	}

	public static LocalStorageAccessFolder folder(LocalStorageAccessFolder parent, DocumentFile directory) {
		return new LocalStorageAccessFolder(parent, //
				directory.getName(), //
				getNodePath(parent, directory.getName()), //
				DocumentsContract.getDocumentId(directory.getUri()), //
				directory.getUri().toString());
	}

	public static LocalStorageAccessFile file(LocalStorageAccessFolder parent, DocumentFile documentFile) {
		return new LocalStorageAccessFile( //
				parent, //
				documentFile.getName(), //
				getNodePath(parent, documentFile.getName()), //
				Optional.of(documentFile.length()), //
				Optional.of(new Date(documentFile.lastModified())), //
				DocumentsContract.getTreeDocumentId(documentFile.getUri()), //
				documentFile.getUri().toString());
	}

	public static LocalStorageAccessFile file(LocalStorageAccessFolder parent, String name, Optional<Long> size) {
		return new LocalStorageAccessFile(//
				parent, //
				name, //
				getNodePath(parent, name), //
				size, //
				Optional.empty(), //
				null, //
				null);
	}

	public static LocalStorageAccessFile file(LocalStorageAccessFolder parent, String name, String path, Optional<Long> size, String documentId) {
		return new LocalStorageAccessFile(parent, //
				name, //
				path, //
				size, //
				Optional.empty(), //
				documentId, //
				getDocumentUri(parent, documentId));
	}

	public static LocalStorageAccessFolder folder(LocalStorageAccessFolder parent, String name) {
		return new LocalStorageAccessFolder(parent, //
				name, //
				getNodePath(parent, name), //
				null, //
				null);
	}

	public static LocalStorageAccessFolder folder(LocalStorageAccessFolder parent, String name, String documentId) {
		return new LocalStorageAccessFolder(parent, //
				name, //
				getNodePath(parent, name), //
				documentId, //
				getDocumentUri(parent, documentId));
	}

	private static String getDocumentUri(LocalStorageAccessFolder parent, String documentId) {
		return DocumentsContract.buildDocumentUriUsingTree(parent.getUri(), documentId).toString();
	}

	private static boolean isFolder(DocumentFile file) {
		return file.isDirectory();
	}

	private static boolean isFolder(Cursor cursor) {
		return cursor.getString(1).equals(DocumentsContract.Document.MIME_TYPE_DIR);
	}

	public static String getNodePath(LocalStorageAccessFolder parent, String name) {
		return parent.getPath() + "/" + name;
	}

}
