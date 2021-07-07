package org.cryptomator.data.cloud.crypto

import android.util.LruCache
import org.cryptomator.data.cloud.crypto.DirIdCache.DirIdInfo
import org.cryptomator.domain.CloudFile
import java.util.Date

internal class DirIdCacheFormatPre7 : DirIdCache {

	private val cache = LruCache<DirIdCacheKey, DirIdInfo>(MAX_SIZE)

	override fun get(folder: CryptoFolder): DirIdInfo? {
		return cache[DirIdCacheKey.toKey(folder)]
	}

	override fun put(folder: CryptoFolder, dirIdInfo: DirIdInfo): DirIdInfo {
		val key = DirIdCacheKey.toKey(folder)
		cache.put(key, dirIdInfo)
		cache.remove(key.withoutModified())
		return dirIdInfo
	}

	override fun evict(folder: CryptoFolder) {
		val key = DirIdCacheKey.toKey(folder)
		cache.remove(key)
		cache.remove(key.withoutModified())
	}

	override fun evictSubFoldersOf(cryptoFolder: CryptoFolder) {
		// no implementation needed
	}

	private class DirIdCacheKey {

		private val path: String?
		private val modified: Date?

		private constructor(dirFile: CloudFile?) {
			path = dirFile?.path
			modified = dirFile?.modified
		}

		private constructor(path: String?) {
			this.path = path
			modified = null
		}

		fun withoutModified(): DirIdCacheKey {
			return DirIdCacheKey(path)
		}

		override fun equals(other: Any?): Boolean {
			if (other === this) {
				return true
			}
			return if (other == null || javaClass != other.javaClass) {
				false
			} else internalEquals(other as DirIdCacheKey)
		}

		private fun internalEquals(o: DirIdCacheKey): Boolean {
			return ((if (path == null) o.path == null else path == o.path) //
					&& if (modified == null) o.modified == null else modified == o.modified)
		}

		override fun hashCode(): Int {
			val prime = 31
			var hash = 1940604225
			hash = hash * prime + (path?.hashCode() ?: 0)
			hash = hash * prime + (modified?.hashCode() ?: 0)
			return hash
		}

		companion object {

			fun toKey(folder: CryptoFolder): DirIdCacheKey {
				return DirIdCacheKey(folder.dirFile)
			}
		}
	}

	companion object {

		private const val MAX_SIZE = 1024
	}
}
