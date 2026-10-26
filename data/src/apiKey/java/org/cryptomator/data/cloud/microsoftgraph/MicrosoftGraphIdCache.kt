package org.cryptomator.data.cloud.microsoftgraph

import android.util.LruCache
import org.cryptomator.domain.CloudFolder
import javax.inject.Inject

internal class MicrosoftGraphIdCache @Inject constructor() {

	private val cache: LruCache<String, NodeInfo> = LruCache(1000)

	operator fun get(path: String): NodeInfo? {
		return cache[path]
	}

	fun <T : MicrosoftGraphIdCloudNode> cache(value: T): T {
		add(value)
		return value
	}

	private fun add(node: MicrosoftGraphIdCloudNode) {
		add(node.path, NodeInfo(node))
	}

	fun add(path: String, info: NodeInfo) {
		cache.put(path, info)
	}

	fun remove(node: MicrosoftGraphIdCloudNode) {
		remove(node.path)
	}

	fun remove(path: String) {
		removeChildren(path)
		cache.remove(path)
	}

	fun removeChildren(path: String) {
		val prefix = "$path/"
		for (key in cache.snapshot().keys) {
			if (key.startsWith(prefix)) {
				cache.remove(key)
			}
		}
	}

	internal class NodeInfo(val id: String, val driveId: String?, val isFolder: Boolean, private val cTag: String?) {
		constructor(node: MicrosoftGraphIdCloudNode) : this(node.id, node.driveId, node is CloudFolder, "")

		fun getcTag(): String? {
			return cTag
		}
	}

}
