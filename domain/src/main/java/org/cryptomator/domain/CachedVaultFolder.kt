package org.cryptomator.domain

/**
 * A folder of an unlocked vault as the cache of the vault's documents knows it, so that it can be shown without asking
 * the cloud. Its [cloud] is the vault's decrypted view and its [path] has the form of the vault's own nodes, e.g.
 * `/a/b`, `` for the root. Its [documentId], if known, finds it again after it was renamed or moved, e.g. by another app.
 */
class CachedVaultFolder(override val cloud: Cloud?, override val path: String, val documentId: String? = null) : CloudFolder {

	override val name: String
		get() = path.substringAfterLast('/')

	override val parent: CachedVaultFolder?
		get() = if (path.isEmpty()) null else CachedVaultFolder(cloud, path.substringBeforeLast('/'))

	override fun withCloud(cloud: Cloud?): CachedVaultFolder {
		return CachedVaultFolder(cloud, path, documentId)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		return other is CachedVaultFolder && path == other.path
	}

	override fun hashCode(): Int {
		return path.hashCode()
	}
}
