package org.cryptomator.data.cloud.crypto

import android.util.LruCache
import org.cryptomator.data.cloud.crypto.DirIdCache.DirIdInfo

internal class DirIdCacheFormat7 : DirIdCache {

	private val cache = LruCache<DirIdCacheKey, DirIdInfo>(MAX_SIZE)

	override fun get(folder: CryptoFolder): DirIdInfo? {
		return cache[DirIdCacheKey.toKey(folder)]
	}

	override fun put(folder: CryptoFolder, dirIdInfo: DirIdInfo): DirIdInfo {
		val key = DirIdCacheKey.toKey(folder)
		cache.put(key, dirIdInfo)
		return dirIdInfo
	}

	override fun evict(folder: CryptoFolder) {
		val key = DirIdCacheKey.toKey(folder)
		cache.remove(key)
	}

	override fun evictSubFoldersOf(cryptoFolder: CryptoFolder) {
		val cacheSnapshot = cache.snapshot()
		cacheSnapshot.forEach { (key) ->
			if (key.path?.startsWith(cryptoFolder.path + "/") == true) {
				cache.remove(key)
			}
		}
	}

	private class DirIdCacheKey private constructor(path: String) {

		val path: String?

		override fun equals(other: Any?): Boolean {
			if (other === this) {
				return true
			}
			return if (other == null || javaClass != other.javaClass) {
				false
			} else internalEquals(other as DirIdCacheKey)
		}

		private fun internalEquals(o: DirIdCacheKey): Boolean {
			return if (path == null) o.path == null else path == o.path
		}

		override fun hashCode(): Int {
			val prime = 31
			var hash = 1940604225
			hash = hash * prime + (path?.hashCode() ?: 0)
			return hash
		}

		companion object {

			fun toKey(folder: CryptoFolder): DirIdCacheKey {
				return DirIdCacheKey(folder.path)
			}
		}

		init {
			this.path = path
		}
	}

	companion object {

		private const val MAX_SIZE = 1024
	}
}
