package org.cryptomator.data.cloud.pcloud;

import android.util.LruCache;

import org.cryptomator.domain.CloudFolder;

import javax.inject.Inject;

class PCloudIdCache {

	private final LruCache<String, NodeInfo> cache;

	@Inject
	PCloudIdCache() {
		cache = new LruCache<>(1000);
	}

	public NodeInfo get(String path) {
		return cache.get(path);
	}

	<T extends PCloudNode> T cache(T value) {
		add(value);
		return value;
	}

	public void add(PCloudNode node) {
		add(node.getPath(), new NodeInfo(node));
	}

	private void add(String path, NodeInfo info) {
		cache.put(path, info);
	}

	public void remove(PCloudNode node) {
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

		private final Long id;
		private final boolean isFolder;

		private NodeInfo(PCloudNode node) {
			this(node.getId(), node instanceof CloudFolder);
		}

		NodeInfo(Long id, boolean isFolder) {
			this.id = id;
			this.isFolder = isFolder;
		}

		public Long getId() {
			return id;
		}

		public boolean isFolder() {
			return isFolder;
		}

	}

}
