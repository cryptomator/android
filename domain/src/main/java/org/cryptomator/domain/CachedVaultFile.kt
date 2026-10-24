package org.cryptomator.domain

import java.io.File
import java.util.Date

/**
 * A file of an unlocked vault as the cache of the vault's documents knows it, see [CachedVaultFolder]. [upload] tells
 * whether a local change of it waits for its upload or that upload failed, and [thumbnail] is a preview of an image, if
 * one has been made.
 */
class CachedVaultFile(
	override val parent: CachedVaultFolder,
	override val name: String,
	override val size: Long?,
	override val modified: Date?,
	val upload: Upload,
	val thumbnail: File?
) : CloudFile {

	enum class Upload {
		NONE, PENDING, FAILED
	}

	override val cloud: Cloud?
		get() = parent.cloud

	override val path: String
		get() = "${parent.path}/$name"

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		return other is CachedVaultFile && path == other.path
	}

	override fun hashCode(): Int {
		return path.hashCode()
	}
}
