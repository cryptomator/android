package org.cryptomator.data.cloud.crypto

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import org.cryptomator.domain.CloudFolder

open class CryptoFolder(
	override val parent: CryptoFolder?, override val name: String, override val path: String,
	/**
	 * @return the file containing the directory id, in the underlying, i.e. decorated, CloudContentRepository
	 */
	val dirFile: CloudFile?
) : CloudFolder, CryptoNode {

	override val cloud: Cloud?
		get() = parent?.cloud

	override fun equals(other: Any?): Boolean {
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		return if (other === this) {
			true
		} else internalEquals(other as CryptoFolder)
	}

	private fun internalEquals(obj: CryptoFolder): Boolean {
		return path == obj.path
	}

	override fun hashCode(): Int {
		return path.hashCode()
	}

	override fun withCloud(cloud: Cloud?): CryptoFolder? {
		return CryptoFolder(parent?.withCloud(cloud), name, path, dirFile)
	}
}
