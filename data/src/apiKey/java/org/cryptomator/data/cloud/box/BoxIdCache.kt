package org.cryptomator.data.cloud.box

import android.util.LruCache

internal class BoxIdCache {

	private val cache: LruCache<String, NodeInfo> = LruCache(1000)

	operator fun get(path: String): NodeInfo? {
		return cache[path]
	}

	/**
	 * Drops the cached descendants as well if the path now belongs to another item, as theirs belong to the replaced one.
	 */
	fun add(path: String, info: NodeInfo) {
		if (cache[path]?.id?.equals(info.id) == false) {
			removeChildren(path)
		}
		cache.put(path, info)
	}

	fun remove(path: String) {
		removeChildren(path)
		cache.remove(path)
	}

	fun childPaths(path: String): List<String> {
		val prefix = "$path/"
		return cache.snapshot().keys.filter { it.startsWith(prefix) && it.indexOf('/', prefix.length) == -1 }
	}

	private fun removeChildren(path: String) {
		val prefix = "$path/"
		for (key in cache.snapshot().keys) {
			if (key.startsWith(prefix)) {
				cache.remove(key)
			}
		}
	}

	internal class NodeInfo(val id: String, val isFolder: Boolean) {

		constructor(item: BoxItem) : this(item.id, item.isFolder)
	}
}
