package org.cryptomator.data.cloud.local

import android.content.ContentResolver
import android.content.Context
import android.content.UriPermission
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.cryptomator.data.cloud.local.LocalStorageAccessFrameworkNodeFactory.file
import org.cryptomator.data.cloud.local.LocalStorageAccessFrameworkNodeFactory.folder
import org.cryptomator.data.cloud.local.LocalStorageAccessFrameworkNodeFactory.from
import org.cryptomator.data.cloud.local.LocalStorageAccessFrameworkNodeFactory.getNodePath
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.LocalStorageCloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.NotFoundException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.exception.authentication.NoAuthenticationProvidedException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import org.cryptomator.util.file.MimeType
import org.cryptomator.util.file.MimeTypes
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.ArrayList
import java.util.function.Supplier

internal class LocalStorageAccessFrameworkImpl(context: Context, private val mimeTypes: MimeTypes, cloud: LocalStorageCloud, documentIdCache: DocumentIdCache) {

	private val context: Context
	private val root: RootLocalStorageAccessFolder
	private val idCache: DocumentIdCache

	private fun hasUriPermissions(context: Context, uri: String): Boolean {
		val uriPermission = uriPermissionFor(context, uri)
		return uriPermission != null && uriPermission.isReadPermission && uriPermission.isWritePermission
	}

	private fun uriPermissionFor(context: Context, uri: String): UriPermission? {
		return context
			.contentResolver
			.persistedUriPermissions
			.find { uriPermission -> uriPermission.uri.toString() == uri }
	}

	fun root(): LocalStorageAccessFolder {
		return root
	}

	@Throws(BackendException::class)
	fun resolve(path: String): LocalStorageAccessFolder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder: LocalStorageAccessFolder = root
		for (name in names) {
			folder = folder(folder, name)
		}
		return folder
	}

	@Throws(BackendException::class)
	fun file(parent: LocalStorageAccessFolder, name: String, size: Long?): LocalStorageAccessFile {
		if (parent.documentId == null) {
			return LocalStorageAccessFrameworkNodeFactory.file(parent, name, size)
		}
		val path = getNodePath(parent, name)
		val nodeInfo = idCache[path]
		if (nodeInfo != null && !nodeInfo.isFolder && nodeInfo.id != null) {
			return file(parent, name, path, size, nodeInfo.id)
		}
		listFilesWithNameFilter(parent, name).getOrNull(0)?.let {
			if(it is LocalStorageAccessFile) {
				return idCache.cache(it)
			}
		}
		return LocalStorageAccessFrameworkNodeFactory.file(parent, name, size)
	}

	@Throws(BackendException::class)
	fun folder(parent: LocalStorageAccessFolder, name: String): LocalStorageAccessFolder {
		if (parent.documentId == null) {
			return LocalStorageAccessFrameworkNodeFactory.folder(parent, name)
		}
		val path = getNodePath(parent, name)
		val nodeInfo = idCache[path]
		if (nodeInfo != null && nodeInfo.isFolder && nodeInfo.id != null) {
			return folder(parent, name, nodeInfo.id)
		}
		listFilesWithNameFilter(parent, name).getOrNull(0)?.let {
			if(it is LocalStorageAccessFolder) {
				return idCache.cache(it)
			}
		}
		return LocalStorageAccessFrameworkNodeFactory.folder(parent, name)
	}

	@Throws(BackendException::class)
	private fun listFilesWithNameFilter(parent: LocalStorageAccessFolder, name: String): List<LocalStorageAccessNode> {
		var parent = parent
		if (parent.uri == null) {
			parent.parent?.let {
				val parents = listFilesWithNameFilter(it, parent.name)
				if (parents.isEmpty() || parents[0] !is LocalStorageAccessFolder) {
					throw NoSuchCloudFileException(name)
				}
				parent = parents[0] as LocalStorageAccessFolder
			} ?: throw ParentFolderIsNullException(parent.name)
		}

		val result: MutableList<LocalStorageAccessNode> = ArrayList()
		try {
			contentResolver() //
				.query( //
					DocumentsContract.buildChildDocumentsUriUsingTree( //
						parent.uri,  //
						parent.documentId
					), arrayOf(
						DocumentsContract.Document.COLUMN_DISPLAY_NAME,  // cursor position 0
						DocumentsContract.Document.COLUMN_MIME_TYPE,  // cursor position 1
						DocumentsContract.Document.COLUMN_SIZE,  // cursor position 2
						DocumentsContract.Document.COLUMN_LAST_MODIFIED,  // cursor position 3
						DocumentsContract.Document.COLUMN_DOCUMENT_ID // cursor position 4
					),  //
					null,  //
					null,  //
					null
				)?.use {
					while (it.moveToNext()) {
						if (it.getString(0) == name) {
							result.add(idCache.cache(from(parent, it)))
						}
					}
				}
			return result
		} catch (e: IllegalArgumentException) {
			if (e.message?.contains(FileNotFoundException::class.java.canonicalName!!) == true) {
				throw NoSuchCloudFileException(name)
			}
			throw FatalBackendException(e)
		}
	}

	@Throws(BackendException::class)
	fun exists(node: LocalStorageAccessNode): Boolean {
		node.parent?.let {
			return try {
				return listFilesWithNameFilter(it, node.name).getOrNull(0)?.also {
					idCache.add(it)
				} != null
			} catch (e: NoSuchCloudFileException) {
				false
			}
		} ?: throw ParentFolderIsNullException(node.name)
	}

	@Throws(BackendException::class)
	fun list(folder: LocalStorageAccessFolder): List<LocalStorageAccessNode> {
		val result: MutableList<LocalStorageAccessNode> = ArrayList()
		contentResolver() //
			.query( //
				DocumentsContract.buildChildDocumentsUriUsingTree( //
					folder.uri,  //
					folder.documentId
				), arrayOf( //
					DocumentsContract.Document.COLUMN_DISPLAY_NAME,  // cursor position 0
					DocumentsContract.Document.COLUMN_MIME_TYPE,  // cursor position 1
					DocumentsContract.Document.COLUMN_SIZE,  // cursor position 2
					DocumentsContract.Document.COLUMN_LAST_MODIFIED,  // cursor position 3
					DocumentsContract.Document.COLUMN_DOCUMENT_ID // cursor position 4
				), null, null, null
			)?.use {
				while (it.moveToNext()) {
					result.add(idCache.cache(from(folder, it)))
				}
			}
		return result
	}

	@Throws(BackendException::class)
	fun create(folder: LocalStorageAccessFolder): LocalStorageAccessFolder {
		var folder = folder
		folder.parent?.let { foldersParent ->
			if (foldersParent.documentId == null) {
				folder = LocalStorageAccessFolder( //
					create(foldersParent),
					folder.name,  //
					folder.path,  //
					null,  //
					null
				)
			}
		} ?: throw ParentFolderIsNullException(folder.name)

		folder.parent?.let { foldersParent ->
			foldersParent.uri?.let { foldersParentUri ->
				val createdDocument = try {
					DocumentsContract.createDocument( //
						contentResolver(),  //
						foldersParentUri,
						DocumentsContract.Document.MIME_TYPE_DIR,  //
						folder.name
					)
				} catch (e: FileNotFoundException) {
					throw NoSuchCloudFileException(folder.name)
				} ?: throw FatalBackendException("Failed to create document for unknown reason")

				return idCache.cache(folder(foldersParent, buildDocumentFile(createdDocument)))
			} ?: throw FatalBackendException("FoldersParentsUri shouldn't be null")
		} ?: throw ParentFolderIsNullException(folder.name)
	}

	@Throws(BackendException::class)
	fun move(source: LocalStorageAccessNode, target: LocalStorageAccessNode): LocalStorageAccessNode {
		source.parent?.let { sourcesParent ->
			if (exists(target)) {
				throw CloudNodeAlreadyExistsException(target.name)
			}
			idCache.remove(source)
			idCache.remove(target)
			val isRename = source.name != target.name
			val isMove = sourcesParent != target.parent
			var renamedSource = source
			if (isRename) {
				renamedSource = rename(source, target.name)
			}
			return if (isMove) {
				idCache.cache(internalMove(renamedSource, target))
			} else renamedSource
		} ?: throw ParentFolderIsNullException(source.name)
	}

	@Throws(NoSuchCloudFileException::class)
	private fun rename(source: LocalStorageAccessNode, name: String): LocalStorageAccessNode {
		source.parent?.let { parent ->
			var newUri = try {
				requireNotNull(source.uri)
				DocumentsContract.renameDocument(contentResolver(), source.uri!!, name)
			} catch (e: FileNotFoundException) {
				/* Bug in Android 9 see #460 TLDR; In this renameDocument-method, Android 9 throws
					a `FileNotFoundException` although the file exists and is also renamed. */
				if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) {
					throw NoSuchCloudFileException(source.name)
				}
				null
			}

			/* Bug in Android 9 see #460 TLDR; In this renameDocument-method, Android 9 throws
				a `FileNotFoundException` although the file exists and is also renamed. */
			if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
				newUri = try {
					listFilesWithNameFilter(parent, name).getOrNull(0)?.uri
				} catch (e: BackendException) {
					throw FatalBackendException("Failed to list file while move of ${source.name}", e)
				} ?: throw FatalBackendException("Failed to list file while move of ${source.name} for unkown reason")
			}

			requireNotNull(newUri)

			return from(parent, buildDocumentFile(newUri))
		} ?: throw ParentFolderIsNullException(source.name)
	}

	@Throws(NoSuchCloudFileException::class)
	private fun internalMove(source: LocalStorageAccessNode, target: LocalStorageAccessNode): LocalStorageAccessNode {
		source.uri?.let { sourceUri ->
			source.parent?.uri?.let { sourcesParentUri ->
				target.parent?.let { targetsParent ->
					target.parent?.uri?.let { targetsParentUri ->
						val movedTargetUri = try {
							DocumentsContract.moveDocument(contentResolver(), sourceUri, sourcesParentUri, targetsParentUri)
						} catch (e: FileNotFoundException) {
							throw NoSuchCloudFileException(source.name)
						} ?: throw FatalBackendException("Move failed for unknown reason")
						return from(targetsParent, buildDocumentFile(movedTargetUri))
					} ?: throw FatalBackendException("Target parents uri shouldn't be null")
				} ?: throw FatalBackendException("Targets parent shouldn't be null")
			} ?: throw FatalBackendException("Source parents uri shouldn't be null")
		} ?: throw FatalBackendException("Source uri shouldn't be null")


	}

	@Throws(IOException::class, BackendException::class)
	fun write(file: LocalStorageAccessFile, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): LocalStorageAccessFile {
		var file = file
		progressAware.onProgress(Progress.started(UploadState.upload(file)))
		val fileUri = existingFileUri(file)

		if (!replace && fileUri != null) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}

		if (file.parent.uri == null) {
			file.parent.parent?.let {
				val parent = listFilesWithNameFilter(it, file.parent.name)[0] as LocalStorageAccessFolder
				val tmpFileUri = fileUri?.toString() ?: ""
				file = LocalStorageAccessFile(parent, file.name, file.path, file.size, file.modified, file.documentId, tmpFileUri)
			} ?: throw ParentFolderIsNullException(file.parent.name)
		}
		val tmpFile = file
		val uploadUri: Uri = (fileUri ?: createNewDocumentSupplier(tmpFile).get()) ?: throw NotFoundException(tmpFile.name)

		data.open(context)?.use { inputStream ->
			contentResolver().openOutputStream(uploadUri)?.use { out ->
				object : TransferredBytesAwareInputStream(inputStream) {
					override fun bytesTransferred(transferred: Long) {
						progressAware //
							.onProgress(
								Progress.progress(UploadState.upload(tmpFile)) //
									.between(0) //
									.and(size) //
									.withValue(transferred)
							)
					}
				}.use { inputStream ->
					if (out is FileOutputStream) {
						out.channel.truncate(0)
					}
					CopyStream.copyStreamToStream(inputStream, out)
				}
			} ?: throw FatalBackendException("OutputStream shouldn't bee null")
		} ?: throw FatalBackendException("InputStream shouldn't bee null")

		progressAware.onProgress(Progress.completed(UploadState.upload(file)))
		return file(file.parent, buildDocumentFile(uploadUri))
	}

	private fun createNewDocumentSupplier(file: LocalStorageAccessFile): Supplier<Uri?> {
		return Supplier {
			file.parent.uri?.let {
				val mimeType = if (mimeTypes.fromFilename(file.name) == null) MimeType.APPLICATION_OCTET_STREAM else mimeTypes.fromFilename(file.name)
				try {
					DocumentsContract.createDocument(contentResolver(), it, mimeType.toString(), file.name) // FIXME
				} catch (e: FileNotFoundException) {
					null
				}
			}
		}
	}

	@Throws(BackendException::class)
	private fun existingFileUri(file: LocalStorageAccessFile): Uri? {
		return listFilesWithNameFilter(file.parent, file.name).getOrNull(0)?.uri
	}

	@Throws(IOException::class)
	fun read(file: LocalStorageAccessFile, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		contentResolver().openInputStream(file.uri)?.use { inputStream ->
			object : TransferredBytesAwareOutputStream(data) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress(
						Progress.progress(DownloadState.download(file)) //
							.between(0) //
							.and(file.size ?: Long.MAX_VALUE) //
							.withValue(transferred)
					)
				}
			}.use { out -> CopyStream.copyStreamToStream(inputStream, out) }
		} ?: throw FatalBackendException("InputStream shouldn't bee null")
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	@Throws(NoSuchCloudFileException::class)
	fun delete(node: LocalStorageAccessNode) {
		requireNotNull(node.uri)
		try {
			DocumentsContract.deleteDocument(contentResolver(), node.uri!!)
		} catch (e: FileNotFoundException) {
			throw NoSuchCloudFileException(node.name)
		}
		idCache.remove(node)
	}

	private fun buildDocumentFile(fileUri: Uri): DocumentFile {
		// can only be zero on devices with pre-Kitkat, which is excluded by the minSDK
		return DocumentFile.fromSingleUri(context, fileUri)!!
	}

	private fun contentResolver(): ContentResolver {
		return context.contentResolver
	}

	init {
		if (!hasUriPermissions(context, cloud.rootUri())) {
			throw NoAuthenticationProvidedException(cloud)
		}
		this.context = context
		this.root = RootLocalStorageAccessFolder(cloud)
		idCache = documentIdCache
	}
}
