package org.cryptomator.data.cloud.crypto;

import java.util.Map;

import android.util.LruCache;

class DirIdCacheFormat7 implements DirIdCache {

	private static final int MAX_SIZE = 1024;

	private final LruCache<DirIdCacheKey, DirIdInfo> cache = new LruCache<>(MAX_SIZE);

	DirIdCacheFormat7() {
	}

	@Override
	public DirIdInfo get(CryptoFolder folder) {
		return cache.get(DirIdCacheKey.toKey(folder));
	}

	@Override
	public DirIdInfo put(CryptoFolder folder, DirIdInfo dirIdInfo) {
		DirIdCacheKey key = DirIdCacheKey.toKey(folder);
		cache.put(key, dirIdInfo);
		return dirIdInfo;
	}

	@Override
	public void evict(CryptoFolder folder) {
		DirIdCacheKey key = DirIdCacheKey.toKey(folder);
		cache.remove(key);
	}

	@Override
	public void evictSubFoldersOf(CryptoFolder folder) {
		Map<DirIdCacheKey, DirIdInfo> cacheSnapshot = cache.snapshot();
		for (Map.Entry<DirIdCacheKey, DirIdInfo> cacheEntry : cacheSnapshot.entrySet()) {
			if (cacheEntry.getKey().path.startsWith(folder.getPath() + "/")) {
				cache.remove(cacheEntry.getKey());
			}
		}
	}

	private static class DirIdCacheKey {

		static DirIdCacheKey toKey(CryptoFolder folder) {
			return new DirIdCacheKey(folder.getPath());
		}

		private final String path;

		private DirIdCacheKey(String path) {
			this.path = path;
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
			return (path == null ? o.path == null : path.equals(o.path));
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int hash = 1940604225;
			hash = hash * prime + (path == null ? 0 : path.hashCode());
			return hash;
		}

	}
}
