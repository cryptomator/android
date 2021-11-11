package org.cryptomator.data.cloud.local

import android.net.Uri
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.CloudFolder

open class LocalStorageAccessFolder(override val parent: LocalStorageAccessFolder?, override val name: String, override val path: String, override val documentId: String?, private val documentUri: String?) :
	CloudFolder, LocalStorageAccessNode {

	override val cloud: Cloud?
		get() = parent?.cloud
	override val uri: Uri?
		get() = if (documentUri == null) {
			null
		} else Uri.parse(documentUri)

	override fun equals(other: Any?): Boolean {
		if (other === this) {
			return true
		}
		return if (other == null || javaClass != other.javaClass) {
			false
		} else internalEquals(other as LocalStorageAccessFolder)
	}

	private fun internalEquals(o: LocalStorageAccessFolder): Boolean {
		return path == o.path
	}

	override fun hashCode(): Int {
		val prime = 31
		var hash = 341797327
		hash = hash * prime + path.hashCode()
		return hash
	}

	override fun withCloud(cloud: Cloud?): LocalStorageAccessFolder? {
		return LocalStorageAccessFolder(parent?.withCloud(cloud), name, path, documentId, documentUri)
	}
}
