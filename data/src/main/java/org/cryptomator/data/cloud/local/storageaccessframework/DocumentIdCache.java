package org.cryptomator.data.cloud.local.storageaccessframework;

import android.util.LruCache;

import org.cryptomator.domain.CloudFolder;

class DocumentIdCache {

	private final LruCache<String, NodeInfo> cache;

	DocumentIdCache() {
		cache = new LruCache<>(1000);
	}

	public NodeInfo get(String path) {
		return cache.get(path);
	}

	<T extends LocalStorageAccessNode> T cache(T value) {
		add(value);
		return value;
	}

	public void add(LocalStorageAccessNode node) {
		add(node.getPath(), new NodeInfo(node));
	}

	private void add(String path, NodeInfo info) {
		cache.put(path, info);
	}

	public void remove(LocalStorageAccessNode node) {
		remove(node.getPath());
	}

	private void remove(String path) {
		removeChildren(path);
		cache.remove(path);
	}

	private void removeChildren(String path) {
		String prefix = path + '/';
		for (String key : cache.snapshot().keySet()) {
			if (key.startsWith(prefix)) {
				cache.remove(key);
			}
		}
	}

	static class NodeInfo {

		private final String id;
		private final boolean isFolder;

		private NodeInfo(LocalStorageAccessNode node) {
			this(node.getDocumentId(), node instanceof CloudFolder);
		}

		NodeInfo(String id, boolean isFolder) {
			this.id = id;
			this.isFolder = isFolder;
		}

		public String getId() {
			return id;
		}

		public boolean isFolder() {
			return isFolder;
		}

	}

}
