package org.cryptomator.data.cloud.s3

import android.content.Context
import org.cryptomator.data.cloud.s3.S3CloudApiExceptions.isAccessProblem
import org.cryptomator.data.util.CopyStream
import org.cryptomator.data.util.TransferredBytesAwareInputStream
import org.cryptomator.data.util.TransferredBytesAwareOutputStream
import org.cryptomator.domain.S3Cloud
import org.cryptomator.domain.exception.BackendException
import org.cryptomator.domain.exception.CloudNodeAlreadyExistsException
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.ForbiddenException
import org.cryptomator.domain.exception.NoSuchBucketException
import org.cryptomator.domain.exception.NoSuchCloudFileException
import org.cryptomator.domain.exception.ParentFolderIsNullException
import org.cryptomator.domain.exception.authentication.WrongCredentialsException
import org.cryptomator.domain.usecases.ProgressAware
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.domain.usecases.cloud.DownloadState
import org.cryptomator.domain.usecases.cloud.Progress
import org.cryptomator.domain.usecases.cloud.UploadState
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Date
import java.util.LinkedList
import io.minio.CopyObjectArgs
import io.minio.CopySource
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.RemoveObjectsArgs
import io.minio.Result
import io.minio.StatObjectArgs
import io.minio.errors.ErrorResponseException
import io.minio.messages.DeleteObject
import io.minio.messages.Item
import timber.log.Timber

internal class S3Impl(private val cloud: S3Cloud, private val client: MinioClient, private val context: Context) {

	private val root = RootS3Folder(cloud)

	fun root(): S3Folder {
		return root
	}

	fun resolve(path: String): S3Folder {
		val names = path.removePrefix("/").split("/").toTypedArray()
		var folder: S3Folder = root
		for (name in names) {
			if (name.isEmpty().not()) {
				folder = folder(folder, name)
			}
		}
		return folder
	}

	@Throws(BackendException::class, IOException::class)
	fun file(parent: S3Folder, name: String, size: Long?): S3File {
		return S3CloudNodeFactory.file(parent, name, size, parent.key + name)
	}

	fun folder(parent: S3Folder, name: String): S3Folder {
		return S3CloudNodeFactory.folder(parent, name, parent.key + name)
	}

	@Throws(BackendException::class)
	fun exists(node: S3Node): Boolean {
		try {
			return if (node !is RootS3Folder) {
				client.statObject(StatObjectArgs.builder().bucket(cloud.s3Bucket()).`object`(node.key).build())
				true
			} else {
				// if the bucket exists the root folder is there too. Otherwise there is no exists check possible
				try {
					requireBucketExists()
					return true
				} catch (e: NoSuchBucketException) {
					return false
				} catch (e: BackendException) {
					throw FatalBackendException(e)
				}
			}
		} catch (e: ErrorResponseException) {
			if (S3CloudApiErrorCodes.NO_SUCH_KEY.value == e.errorResponse().code()) {
				return false
			}
			throw FatalBackendException(e)
		}
	}

	@Throws(IOException::class, BackendException::class)
	fun list(folder: S3Folder): List<S3Node> {
		val request = ListObjectsArgs.builder().bucket(cloud.s3Bucket()).prefix(folder.key).delimiter(DELIMITER).build()

		val listObjects = client.listObjects(request)
		return try {
			listObjects.mapNotNull {
				run {
					val item = it.get()
					if (item.isDir) {
						S3CloudNodeFactory.folder(folder, S3CloudNodeFactory.getNameFromKey(item.objectName()))
					} else {
						if (item.objectName() != folder.key) {
							S3CloudNodeFactory.file(folder, S3CloudNodeFactory.getNameFromKey(item.objectName()), item.size(), Date.from(item.lastModified().toInstant()))
						} else {
							// skip listed folder itself
							null
						}
					}
				}
			}
		} catch (e: ErrorResponseException) {
			throw handleApiError(e, folder.path)
		}
	}

	@Throws(IOException::class, BackendException::class)
	fun create(folder: S3Folder): S3Folder {
		var folder = folder

		folder.parent?.let { parentFolder ->
			if (!exists(parentFolder)) {
				folder = S3Folder(create(parentFolder), folder.name, folder.path)
			}
		} ?: throw ParentFolderIsNullException(folder.name)

		folder.parent?.let { parentFolder ->
			try {
				val putObjectArgs = PutObjectArgs //
					.builder() //
					.bucket(cloud.s3Bucket()) //
					.`object`(folder.key) //
					.stream(ByteArrayInputStream(ByteArray(0)), 0, -1) //
					.build()
				client.putObject(putObjectArgs)

			} catch (e: ErrorResponseException) {
				throw handleApiError(e, folder.path)
			}

			return S3CloudNodeFactory.folder(parentFolder, folder.name)
		} ?: throw ParentFolderIsNullException(folder.name)
	}

	@Throws(IOException::class, BackendException::class)
	fun move(source: S3Node, target: S3Node): S3Node {
		if (exists(target)) {
			throw CloudNodeAlreadyExistsException(target.name)
		}
		return if (source is S3Folder && target is S3Folder) {
			moveFolder(source, target)
		} else if (source is S3File && target is S3File) {
			moveFile(source, target)
		} else {
			throw FatalBackendException("Can't move file to folder or folder to file")
		}
	}

	@Throws(IOException::class, BackendException::class)
	private fun moveFolder(source: S3Folder, target: S3Folder): S3Folder {
		target.parent?.let { targetsParent ->
			val request = ListObjectsArgs.builder().bucket(cloud.s3Bucket()).prefix(source.key).recursive(true).build()
			val sourceKeysIncludingDescendants = try {
				client.listObjects(request).mapNotNull {
					run {
						it.get().objectName()
					}
				}
			} catch (e: ErrorResponseException) {
				throw handleApiError(e, source.path)
			}

			val objectsToDelete: MutableList<DeleteObject> = LinkedList()

			for (sourceKey in sourceKeysIncludingDescendants) {
				objectsToDelete.add(DeleteObject(sourceKey))

				val copySource = CopySource.builder().bucket(cloud.s3Bucket()).`object`(sourceKey).build()
				val targetKey = target.key + sourceKey.removePrefix(source.key)

				val copyObjectArgs = CopyObjectArgs.builder().bucket(cloud.s3Bucket()).`object`(targetKey).source(copySource).build()
				try {
					client.copyObject(copyObjectArgs)
				} catch (e: ErrorResponseException) {
					throw handleApiError(e, source.path)
				}
			}

			val removeObjectsArgs = RemoveObjectsArgs.builder().bucket(cloud.s3Bucket()).objects(objectsToDelete).build()

			for (result in client.removeObjects(removeObjectsArgs)) {
				try {
					result.get()
				} catch (e: ErrorResponseException) {
					throw handleApiError(e, source.path)
				}
			}

			return S3CloudNodeFactory.folder(targetsParent, target.name)
		} ?: throw ParentFolderIsNullException(target.name)
	}

	@Throws(IOException::class, BackendException::class)
	private fun moveFile(source: S3File, target: S3File): S3File {
		val copySource = CopySource.builder().bucket(cloud.s3Bucket()).`object`(source.key).build()
		val copyObjectArgs = CopyObjectArgs.builder().bucket(cloud.s3Bucket()).`object`(target.key).source(copySource).build()
		try {
			val result = client.copyObject(copyObjectArgs)
			delete(source)
			val lastModified = result.headers().getDate("Last-Modified")
			return S3CloudNodeFactory.file(target.parent, target.name, source.size, lastModified)
		} catch (e: ErrorResponseException) {
			throw handleApiError(e, source.path)
		}
	}

	@Throws(IOException::class, BackendException::class)
	fun write(file: S3File, data: DataSource, progressAware: ProgressAware<UploadState>, replace: Boolean, size: Long): S3File {
		if (!replace && exists(file)) {
			throw CloudNodeAlreadyExistsException("CloudNode already exists and replace is false")
		}

		progressAware.onProgress(Progress.started(UploadState.upload(file)))

		data.open(context)?.use { inputStream ->
			object : TransferredBytesAwareInputStream(inputStream) {
				override fun bytesTransferred(transferred: Long) {
					progressAware.onProgress( //
						Progress.progress(UploadState.upload(file)) //
							.between(0) //
							.and(size) //
							.withValue(transferred)
					)
				}
			}.use {
				try {
					val putObjectArgs = PutObjectArgs //
						.builder() //
						.bucket(cloud.s3Bucket()) //
						.`object`(file.key) //
						.stream(it, data.size(context) ?: Long.MAX_VALUE, -1) //
						.build()

					val objectWriteResponse = client.putObject(putObjectArgs)

					val lastModified = objectWriteResponse.headers().getDate("Last-Modified") ?: run {
						val statObjectResponse = client.statObject(
							StatObjectArgs //
								.builder() //
								.bucket(cloud.s3Bucket()) //
								.`object`(file.key) //
								.build()
						)
						Date.from(statObjectResponse.lastModified().toInstant())
					}

					progressAware.onProgress(Progress.completed(UploadState.upload(file)))

					return S3CloudNodeFactory.file(file.parent, file.name, size, lastModified)
				} catch (e: ErrorResponseException) {
					throw handleApiError(e, file.path)
				}
			}
		} ?: throw FatalBackendException("InputStream shouldn't bee null")
	}

	@Throws(IOException::class, BackendException::class)
	fun read(file: S3File, data: OutputStream, progressAware: ProgressAware<DownloadState>) {
		progressAware.onProgress(Progress.started(DownloadState.download(file)))
		val getObjectArgs = GetObjectArgs.builder().bucket(cloud.s3Bucket()).`object`(file.key).build()
		try {
			client.getObject(getObjectArgs).use { response ->
				object : TransferredBytesAwareOutputStream(data) {
					override fun bytesTransferred(transferred: Long) {
						progressAware.onProgress( //
							Progress.progress(DownloadState.download(file)) //
								.between(0) //
								.and(file.size ?: Long.MAX_VALUE) //
								.withValue(transferred)
						)
					}
				}.use { out -> CopyStream.copyStreamToStream(response, out) }
			}
		} catch (e: ErrorResponseException) {
			throw handleApiError(e, file.path)
		}
		progressAware.onProgress(Progress.completed(DownloadState.download(file)))
	}

	@Throws(IOException::class, BackendException::class)
	fun delete(node: S3Node) = if (node is S3Folder) {
		deleteFolder(node)
	} else {
		deleteFile(node as S3File)
	}

	@Throws(IOException::class, BackendException::class)
	private fun deleteFolder(node: S3Folder) {
		val request = ListObjectsArgs.builder().bucket(cloud.s3Bucket()).prefix(node.key).recursive(true).delimiter(DELIMITER).build()

		val listObjects = client.listObjects(request)

		val objectsToDelete = try {
			listObjects.map {
				run {
					val item = it.get()
					DeleteObject(item.objectName())
				}
			}
		} catch (e: ErrorResponseException) {
			throw handleApiError(e, node.path)
		}

		val removeObjectsArgs = RemoveObjectsArgs.builder().bucket(cloud.s3Bucket()).objects(objectsToDelete).build()
		val results = client.removeObjects(removeObjectsArgs)
		results.forEach { result ->
			try {
				val error = result.get()
				Timber.tag("S3Impl").e("Error in deleting object " + error.objectName() + "; " + error.message())
			} catch (e: ErrorResponseException) {
				throw handleApiError(e, node.path)
			}
		}
	}

	//@Throws(IOException::class, BackendException::class)
	private fun deleteFile(node: S3File) {
		val removeObjectArgs = RemoveObjectArgs.builder().bucket(cloud.s3Bucket()).`object`(node.key).build()
		try {
			client.removeObject(removeObjectArgs)
		} catch (e: ErrorResponseException) {
			throw handleApiError(e, "")
		}
	}

	@Throws(NoSuchBucketException::class, BackendException::class)
	fun checkAuthentication(): String {
		requireBucketExists()
		return ""
	}

	@Throws(NoSuchBucketException::class, BackendException::class)
	private fun requireBucketExists() {
		try {
			val returned: Sequence<Result<Item?>?> = ListObjectsArgs.builder() //
				.bucket(cloud.s3Bucket()) //
				.recursive(true) // //TODO
				.maxKeys(1) // Batch size
				.build() //
				.let { client.listObjectsLimit(it, 1) }
			//returned
			//	|-- <Empty>					No elements in bucket
			//	|-- Result<Err>				Any error
			//	|-- Result<Item>
			//		|-- "name/.bzEmpty"		Web interface folder
			//		|-- "name/"				0 Byte folder
			//		|-- "name"				0 or X Byte file
			//Note: This implementation depends on listObjects/listObjectsLimit returning elements in a sensible order

			val result: Result<Item?> = returned.firstOrNull() ?: return //No item, but above all no exception, ergo: Bucket exists
			result.get() //Throw appropriate exception (if any) implicitly through "handleApiError"
		} catch (e: ErrorResponseException) {
			throw handleApiError(e, cloud.s3Bucket())
		}
	}

	private fun handleApiError(e: ErrorResponseException, name: String): Exception {
		val errorCode = e.errorResponse().code()
		return when {
			isAccessProblem(errorCode) -> {
				ForbiddenException()
			}
			S3CloudApiErrorCodes.NO_SUCH_BUCKET.value == errorCode -> {
				NoSuchBucketException(name)
			}
			S3CloudApiErrorCodes.NO_SUCH_KEY.value == errorCode -> {
				NoSuchCloudFileException(name)
			}
			else -> {
				FatalBackendException(e)
			}
		}
	}

	fun logout() {
		// FIXME what about logout?
	}

	companion object {

		private const val DELIMITER = "/"
	}

	init {
		if (cloud.accessKey() == null || cloud.secretKey() == null) {
			throw WrongCredentialsException(cloud)
		}
	}
}

fun MinioClient.listObjectsLimit(args: ListObjectsArgs, maxObjects: Int): Sequence<Result<Item?>?> {
	return this.listObjects(args).asSequence().constrainOnce().take(maxObjects)
}