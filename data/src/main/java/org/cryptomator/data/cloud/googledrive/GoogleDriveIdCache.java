package org.cryptomator.data.cloud.googledrive;

import android.util.LruCache;

import org.cryptomator.domain.CloudFolder;

import javax.inject.Inject;

class GoogleDriveIdCache {

	private final LruCache<String, NodeInfo> cache;

	@Inject
	GoogleDriveIdCache() {
		cache = new LruCache<>(1000);
	}

	public NodeInfo get(String path) {
		return cache.get(path);
	}

	<T extends GoogleDriveIdCloudNode> T cache(T value) {
		add(value);
		return value;
	}

	public void add(GoogleDriveIdCloudNode node) {
		add(node.getPath(), new NodeInfo(node));
	}

	private void add(String path, NodeInfo info) {
		cache.put(path, info);
	}

	public void remove(GoogleDriveIdCloudNode node) {
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

		private NodeInfo(GoogleDriveIdCloudNode node) {
			this(node.getDriveId(), node instanceof CloudFolder);
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
