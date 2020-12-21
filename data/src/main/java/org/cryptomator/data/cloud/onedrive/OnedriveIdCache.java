package org.cryptomator.data.cloud.onedrive;

import android.util.LruCache;

import org.cryptomator.domain.CloudFolder;

import javax.inject.Inject;

class OnedriveIdCache {

	private final LruCache<String, NodeInfo> cache;

	@Inject
	OnedriveIdCache() {
		cache = new LruCache<>(1000);
	}

	public NodeInfo get(String path) {
		return cache.get(path);
	}

	public <T extends OnedriveIdCloudNode> T cache(T value) {
		add(value);
		return value;
	}

	private void add(OnedriveIdCloudNode node) {
		add(node.getPath(), new NodeInfo(node));
	}

	public void add(String path, NodeInfo info) {
		cache.put(path, info);
	}

	public void remove(OnedriveIdCloudNode node) {
		remove(node.getPath());
	}

	public void remove(String path) {
		removeChildren(path);
		cache.remove(path);
	}

	void removeChildren(String path) {
		String prefix = path + '/';
		for (String key : cache.snapshot().keySet()) {
			if (key.startsWith(prefix)) {
				cache.remove(key);
			}
		}
	}

	static class NodeInfo {

		private final String id;
		private final String driveId;
		private final boolean isFolder;
		private final String cTag;

		private NodeInfo(OnedriveIdCloudNode node) {
			this(node.getId(), node.getDriveId(), node instanceof CloudFolder, "");
		}

		NodeInfo(String id, String driveId, boolean isFolder, String cTag) {
			this.id = id;
			this.driveId = driveId;
			this.isFolder = isFolder;
			this.cTag = cTag;
		}

		public String getId() {
			return id;
		}

		public String getDriveId() {
			return driveId;
		}

		public boolean isFolder() {
			return isFolder;
		}

		public String getcTag() {
			return cTag;
		}

	}

}
