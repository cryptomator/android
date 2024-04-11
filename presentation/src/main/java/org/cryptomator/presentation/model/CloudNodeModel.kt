package org.cryptomator.presentation.model

import android.graphics.Bitmap
import org.cryptomator.domain.CloudNode
import java.io.Serializable

abstract class CloudNodeModel<T : CloudNode> internal constructor(private val cloudNode: T) : Serializable {

	var oldName: String? = null
	var progress: ProgressModel? = null
	var isSelected = false
	var thumbnail: Int = 0 // reference to a file in LRU Cache cloud-related
	
	val name: String
		get() = cloudNode.name
	val simpleName: String
		get() = cloudNode.name.substring(0, cloudNode.name.lastIndexOf("."))
	val path: String
		get() = cloudNode.path
	val parent: CloudFolderModel?
		get() = cloudNode.parent?.let { CloudFolderModel(it) }

	abstract val isFile: Boolean
	abstract val isFolder: Boolean

	fun hasParent(): Boolean {
		return cloudNode.parent != null
	}

	fun toCloudNode(): T {
		return cloudNode
	}

	override fun equals(other: Any?): Boolean {
		if (other === this) return true
		return if (other == null || javaClass != other.javaClass) false else internalEquals(other as CloudNodeModel<*>)
	}

	private fun internalEquals(o: CloudNodeModel<*>): Boolean {
		return cloudNode == o.cloudNode
	}

	override fun hashCode(): Int {
		return cloudNode.hashCode()
	}

}
