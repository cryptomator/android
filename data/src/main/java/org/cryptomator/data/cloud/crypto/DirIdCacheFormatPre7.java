package org.cryptomator.data.cloud.crypto;

import android.util.LruCache;

import org.cryptomator.domain.CloudFile;

import java.util.Date;

class DirIdCacheFormatPre7 implements DirIdCache {

	private static final int MAX_SIZE = 1024;

	private final LruCache<DirIdCacheKey, DirIdInfo> cache = new LruCache<>(MAX_SIZE);

	DirIdCacheFormatPre7() {
	}

	public DirIdInfo get(CryptoFolder folder) {
		return cache.get(DirIdCacheKey.toKey(folder));
	}

	public DirIdInfo put(CryptoFolder folder, DirIdInfo dirIdInfo) {
		DirIdCacheKey key = DirIdCacheKey.toKey(folder);
		cache.put(key, dirIdInfo);
		cache.remove(key.withoutModified());
		return dirIdInfo;
	}

	public void evict(CryptoFolder folder) {
		DirIdCacheKey key = DirIdCacheKey.toKey(folder);
		cache.remove(key);
		cache.remove(key.withoutModified());
	}

	@Override
	public void evictSubFoldersOf(CryptoFolder cryptoFolder) {
		// no implementation needed
	}

	private static class DirIdCacheKey {

		static DirIdCacheKey toKey(CryptoFolder folder) {
			return new DirIdCacheKey(folder.getDirFile());
		}

		private final String path;
		private final Date modified;

		private DirIdCacheKey(CloudFile dirFile) {
			this.path = dirFile == null ? null : dirFile.getPath();
			this.modified = dirFile == null ? null : dirFile.getModified().orElse(null);
		}

		private DirIdCacheKey(String path) {
			this.path = path;
			this.modified = null;
		}

		DirIdCacheKey withoutModified() {
			return new DirIdCacheKey(path);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			if (obj == null || getClass() != obj.getClass())
				return false;
			return internalEquals((DirIdCacheKey) obj);
		}

		private boolean internalEquals(DirIdCacheKey o) {
			return (path == null ? o.path == null : path.equals(o.path)) //
					&& (modified == null ? o.modified == null : modified.equals(o.modified));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int hash = 1940604225;
			hash = hash * prime + (path == null ? 0 : path.hashCode());
			hash = hash * prime + (modified == null ? 0 : modified.hashCode());
			return hash;
		}
	}
}
