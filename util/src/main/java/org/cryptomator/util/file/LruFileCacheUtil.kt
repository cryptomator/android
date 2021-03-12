package org.cryptomator.util.file

import android.content.Context
import android.os.Build
import com.tomclaw.cache.DiskLruCache
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Comparator
import timber.log.Timber

class LruFileCacheUtil(context: Context) {

	private val parent: File = context.cacheDir

	enum class Cache {
		DROPBOX, WEBDAV, PCLOUD, ONEDRIVE, GOOGLE_DRIVE
	}

	fun resolve(cache: Cache?): File {
		return when (cache) {
			Cache.DROPBOX -> File(parent, "LruCacheDropbox")
			Cache.WEBDAV -> File(parent, "LruCacheWebdav")
			Cache.PCLOUD -> File(parent, "LruCachePCloud")
			Cache.ONEDRIVE -> File(parent, "LruCacheOneDrive")
			Cache.GOOGLE_DRIVE -> File(parent, "LruCacheGoogleDrive")
			else -> throw IllegalStateException()
		}
	}

	fun totalSize(): Long {
		return Cache.values()
				.map { size(resolve(it)) }
				.sum()
	}

	private fun size(cacheFolder: File): Long {
		var sum: Long = 0
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			try {
				sum = Files //
						.walk(cacheFolder.toPath()) //
						.map { path: Path -> path.toFile() } //
						.filter { node: File -> node.isFile } //
						.mapToLong { file: File -> file.length() } //
						.sum()
			} catch (e: IOException) {
				if (e !is NoSuchFileException) {
					Timber.tag("LruFileCacheUtil").e(e, "Failed to delete preview image")
				}
			}
		} else {
			sum = getFolderSizeRecursion(cacheFolder)
		}
		return sum
	}

	fun clear() {
		Cache.values().forEach { cache ->
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				try {
					Files.walk(resolve(cache).toPath()) //
							.map { obj: Path -> obj.toFile() } //
							.sorted(Comparator.reverseOrder()) //
							.forEach { obj: File -> obj.delete() }
				} catch (e: IOException) {
					if (e !is NoSuchFileException) {
						Timber.tag("LruFileCacheUtil").e(e, "Failed to delete preview image")
					}
				}
			} else {
				deleteDirectoryRecursion(resolve(cache))
			}
		}
	}

	private fun getFolderSizeRecursion(folder: File): Long {
		var length: Long = 0
		folder.listFiles()?.forEach { file ->
			length += if (file.isFile) {
				file.length()
			} else {
				getFolderSizeRecursion(file)
			}
		}
		return length
	}

	private fun deleteDirectoryRecursion(file: File) {
		if (file.isDirectory) {
			file.listFiles()?.forEach { entry ->
				deleteDirectoryRecursion(entry)
			}
		}
		file.delete()
	}

	companion object {

		@JvmStatic
		@Throws(IOException::class)
		fun retrieveFromLruCache(cacheFile: File, data: OutputStream) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				Files.copy(cacheFile.absoluteFile.toPath(), data)
			} else {
				writeFileContentInData(cacheFile, data)
			}
		}

		@JvmStatic
		@Throws(IOException::class)
		fun storeToLruCache(diskLruCache: DiskLruCache, cacheKey: String?, encryptedTmpFile: File) {
			val storedCacheFile = diskLruCache.put(cacheKey, encryptedTmpFile)
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				Files.copy(storedCacheFile.toPath(), encryptedTmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
			} else {
				FileOutputStream(encryptedTmpFile).use { out -> writeFileContentInData(storedCacheFile, out) }
			}
		}

		@Throws(IOException::class)
		private fun writeFileContentInData(cacheFile: File, data: OutputStream) {
			BufferedInputStream(FileInputStream(cacheFile)).use { `in` ->
				val buffer = ByteArray(2048)
				var lengthRead: Int
				while (`in`.read(buffer).also { lengthRead = it } > 0) {
					data.write(buffer, 0, lengthRead)
					data.flush()
				}
			}
		}
	}

}
