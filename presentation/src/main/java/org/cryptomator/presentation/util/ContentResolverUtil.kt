package org.cryptomator.presentation.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.util.Date
import javax.inject.Inject

class ContentResolverUtil @Inject constructor(context: Context) {

	private val contentResolver: ContentResolver = context.contentResolver

	@Throws(FileNotFoundException::class)
	fun openInputStream(uri: Uri): InputStream? {
		return contentResolver.openInputStream(uri)
	}

	@Throws(FileNotFoundException::class)
	fun openOutputStream(uri: Uri): OutputStream? {
		return contentResolver.openOutputStream(uri)
	}

	fun fileName(uri: Uri): String? {
		return if (isContentUri(uri)) {
			fileNameForContentUri(uri)
		} else {
			extractFileNameFrom(uri)
		}
	}

	fun fileSize(uri: Uri): Long? {
		return when {
			isContentUri(uri) -> {
				fileSizeForContentUri(uri)
			}
			isFileUri(uri) -> {
				fileSizeForFileUri(uri)
			}
			else -> null
		}
	}

	private fun isContentUri(uri: Uri): Boolean {
		return CONTENT_SCHEME == uri.scheme
	}

	private fun isFileUri(uri: Uri): Boolean {
		return FILE_SCHEME == uri.scheme
	}

	fun isFileUriPointingToFolder(uri: Uri): Boolean {
		return isFileUri(uri) && uri.path?.let { File(it).isDirectory } ?: false
	}

	private fun fileNameForContentUri(uri: Uri): String? {
		contentResolver.query(uri, null, null, null, null).use { cursor ->
			cursor?.let {
				if (cursor.moveToFirst()) {
					val nameColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
					if (!cursor.isNull(nameColumnIndex)) {
						return cursor.getString(nameColumnIndex)
					}
				}
			}
			return extractFileNameFrom(uri)
		}
	}

	private fun extractFileNameFrom(uri: Uri): String? {
		uri.path?.let {
			val pathSeparatorIndex = it.lastIndexOf('/')
			if (pathSeparatorIndex != -1) {
				return it.substring(pathSeparatorIndex + 1)
			}
			return it
		} ?: return null
	}

	private fun fileSizeForContentUri(uri: Uri): Long? {
		contentResolver.query(uri, null, null, null, null).use { cursor ->
			if (cursor != null && cursor.moveToFirst()) {
				val sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
				if (!cursor.isNull(sizeColumnIndex)) {
					val size = cursor.getLong(sizeColumnIndex)
					return if (size == 0L) {
						// return unknown if zero to work around that for some files the reported
						// size is zero instead of the actual size
						return null
					} else size
				}
			}
			return null
		}
	}

	private fun fileSizeForFileUri(uri: Uri): Long? {
		return uri.path?.let {
			val file = File(it)
			if (file.exists()) {
				file.length()
			} else {
				null
			}
		}
	}

	fun collectFolderContent(uri: Uri): List<Uri> {
		check(isFileUriPointingToFolder(uri)) { "Invoked collect folder content for URI which is not a file-URI pointing to a folder" }
		val fileUris: MutableList<Uri> = ArrayList()
		uri.path?.let {
			val directory = File(it)
			directory.listFiles()?.forEach { file ->
				when {
					file.isFile -> {
						fileUris.add(Uri.fromFile(file))
					}
					else -> {
						fileUris.addAll(collectFolderContent(Uri.fromFile(file)))
					}
				}
			}
		}
		return fileUris
	}

	fun fileModifiedDate(uri: Uri): Date? {
		return when {
			isContentUri(uri) -> {
				fileModifiedDateForContentUri(uri)
			}
			isFileUri(uri) -> {
				fileModifiedDateForFileUri(uri)
			}
			else -> null
		}
	}

	private fun fileModifiedDateForContentUri(uri: Uri): Date? {
		contentResolver.query(uri, null, null, null, null).use { cursor ->
			if (cursor != null && cursor.moveToFirst()) {
				val dateModifiedColumnIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
				if (!cursor.isNull(dateModifiedColumnIndex)) {
					val date = cursor.getLong(dateModifiedColumnIndex)
					return Date(date);
				}
			}
			// maybe the date of today
			return null
		}
	}

	private fun fileModifiedDateForFileUri(uri: Uri): Date? {
		return uri.path?.let {
			val file = File(it)
			if (file.exists()) {
				Date(file.lastModified())
			} else {
				null
			}
		}
	}

	companion object {

		private const val CONTENT_SCHEME = "content"
		private const val FILE_SCHEME = "file"
	}

}
