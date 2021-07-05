package org.cryptomator.data.cloud.googledrive

import android.util.LruCache
import org.cryptomator.domain.CloudFolder
import javax.inject.Inject

internal class GoogleDriveIdCache @Inject constructor() {

	private val cache: LruCache<String, NodeInfo> = LruCache(1000)

	operator fun get(path: String): NodeInfo? {
		return cache[path]
	}

	fun <T : GoogleDriveIdCloudNode> cache(value: T): T {
		add(value)
		return value
	}

	fun add(node: GoogleDriveIdCloudNode) {
		add(node.path, NodeInfo(node))
	}

	private fun add(path: String, info: NodeInfo) {
		cache.put(path, info)
	}

	fun remove(node: GoogleDriveIdCloudNode) {
		remove(node.path)
	}

	private fun remove(path: String) {
		removeChildren(path)
		cache.remove(path)
	}

	private fun removeChildren(path: String) {
		val prefix = "$path/"
		for (key in cache.snapshot().keys) {
			if (key.startsWith(prefix)) {
				cache.remove(key)
			}
		}
	}

	internal class NodeInfo(val id: String?, val isFolder: Boolean) {

		constructor(node: GoogleDriveIdCloudNode) : this(node.driveId, node is CloudFolder)
	}

}
