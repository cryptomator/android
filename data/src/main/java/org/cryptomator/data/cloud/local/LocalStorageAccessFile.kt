package org.cryptomator.data.cloud.local

import android.net.Uri
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFile
import java.util.Date

class LocalStorageAccessFile(
	override val parent: LocalStorageAccessFolder,
	override val name: String,
	override val path: String,
	override val size: Long?,
	override val modified: Date?,
	override val documentId: String?,
	private val documentUri: String?
) : CloudFile, LocalStorageAccessNode {

	override val cloud: Cloud?
		get() = parent.cloud
	override val uri: Uri
		get() = Uri.parse(documentUri)

	override fun equals(other: Any?): Boolean {
		if (other === this) {
			return true
		}
		return if (other == null || javaClass != other.javaClass) {
			false
		} else internalEquals(other as LocalStorageAccessFile)
	}

	private fun internalEquals(o: LocalStorageAccessFile): Boolean {
		return path == o.path
	}

	override fun hashCode(): Int {
		val prime = 31
		var hash = 56127034
		hash = hash * prime + path.hashCode()
		return hash
	}
}
