package org.cryptomator.data.cloud.local.storageaccessframework

import android.util.LruCache
import org.cryptomator.domain.CloudFolder

internal class DocumentIdCache {

	private val cache: LruCache<String, NodeInfo> = LruCache(1000)

	operator fun get(path: String): NodeInfo? {
		return cache[path]
	}

	fun <T : LocalStorageAccessNode> cache(value: T): T {
		add(value)
		return value
	}

	fun add(node: LocalStorageAccessNode) {
		add(node.path, NodeInfo(node))
	}

	private fun add(path: String, info: NodeInfo) {
		cache.put(path, info)
	}

	fun remove(node: LocalStorageAccessNode) {
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

		constructor(node: LocalStorageAccessNode) : this(node.documentId, node is CloudFolder)

	}

}
