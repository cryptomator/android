package org.cryptomator.data.cloud.crypto

import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.io.File
import java.util.Date

class CryptoFile(
	override val parent: CryptoFolder, override val name: String, override val path: String, override val size: Long?,
	/**
	 * @return The actual file in the underlying, i.e. decorated, CloudContentRepository
	 */
	val cloudFile: CloudFile
) : CloudFile, CryptoNode {

	var thumbnail : File? = null

	override val cloud: Cloud?
		get() = parent.cloud

	override val modified: Date?
		get() = cloudFile.modified

	override fun equals(other: Any?): Boolean {
		if (other == null || javaClass != other.javaClass) {
			return false
		}
		return if (other === this) {
			true
		} else internalEquals(other as CryptoFile)
	}

	private fun internalEquals(obj: CryptoFile): Boolean {
		return path == obj.path
	}

	override fun hashCode(): Int {
		return path.hashCode()
	}
}
