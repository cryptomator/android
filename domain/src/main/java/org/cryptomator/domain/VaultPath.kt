package org.cryptomator.domain

class VaultPath(val vault: Vault, path: String?) {

	val path = normalizePath(path)

	val isRoot: Boolean
		get() = path.isEmpty()

	val name: String
		get() = if (isRoot) vault.name else path.substringAfterLast('/')

	val parent: VaultPath?
		get() = if (isRoot) null else VaultPath(vault, path.substringBeforeLast('/', ""))

	fun resolve(subPath: String): VaultPath {
		return VaultPath(vault, "$path/${normalizePath(subPath)}")
	}

	fun isAnyChildOf(potentialParent: VaultPath): Boolean {
		return vault.id == potentialParent.vault.id && !isRoot && (potentialParent.isRoot || path.startsWith("${potentialParent.path}/"))
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		other as VaultPath
		return vault.id == other.vault.id && path == other.path
	}

	override fun hashCode(): Int {
		return 31 * vault.id.hashCode() + path.hashCode()
	}

	override fun toString(): String {
		return "${vault.id}/$path"
	}
}

fun normalizePath(path: String?): String {
	return path?.trimStart('/')?.trimEnd('/') ?: ""
}

fun VaultPath(vault: Vault): VaultPath {
	return VaultPath(vault, null)
}
