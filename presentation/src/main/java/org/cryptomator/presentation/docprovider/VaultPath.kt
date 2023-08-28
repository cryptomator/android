package org.cryptomator.presentation.docprovider

import org.cryptomator.domain.Vault
import java.io.FileNotFoundException

//TODO Symlinks
class VaultPath(val vault: Vault, path: String?) {

	val path = normalizePath(path)

	val isRoot: Boolean
		get() = path.isEmpty() //TODO .isBlank()?

	val documentId: String
		get() = "${vault.id}/${path}".trimEnd('/')

	val name: String
		get() = if (isRoot) vault.name else path.substringAfterLast('/')

	val parent: VaultPath?
		//TODO Insist on a path not being it's own parent?
		get() = if (isRoot) null else VaultPath(vault, path.substringBeforeLast('/', ""))

	fun resolve(subPath: String): VaultPath {
		//TODO Fix diverging semantics with CloudContentRepository, here: Parent/<Empty> = Parent
		//TODO Sanitize
		return VaultPath("$documentId/${normalizePath(subPath)}")
	}

	fun isAnyChildOf(potentialParent: VaultPath): Boolean {
		//No need for equality check because documentIds can't end with a trailing slash
		return documentId.startsWith("${potentialParent.documentId}/")
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false
		return documentId == (other as VaultPath).documentId
	}

	override fun hashCode(): Int {
		return documentId.hashCode()
	}
}

fun normalizePath(path: String?): String {
	return path?.trimStart('/')?.trimEnd('/') ?: ""
}

fun VaultPath(vault: Vault): VaultPath {
	return VaultPath(vault, null)
}

fun VaultPath(documentId: String): VaultPath {
	//TODO Use substringbefore...
	val elements = documentId.split('/', ignoreCase = false, limit = 2)
	val vaultId = elements.first().let { it.toLongOrNull() ?: throw IllegalArgumentException("Illegal vaultId: $it") }
	return VaultPath(vaultById(vaultId) ?: throw FileNotFoundException("Unknown vault: $vaultId"), elements.getOrNull(1)) //TODO Verify exception
}

private fun vaultById(id: Long): Vault? {
	return appComponent.vaultRepository().vaults().find { it.id == id }
}