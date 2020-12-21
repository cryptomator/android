package org.cryptomator.presentation.model

import org.cryptomator.domain.CloudNode
import org.cryptomator.util.Optional
import java.io.Serializable

abstract class CloudNodeModel<T : CloudNode> internal constructor(private val cloudNode: T) : Serializable {

	var oldName: String? = null
	var progress: Optional<ProgressModel> = Optional.empty()
	var isSelected = false
	val name: String
		get() = cloudNode.name
	val simpleName: String
		get() = cloudNode.name.substring(0, cloudNode.name.lastIndexOf("."))
	val path: String
		get() = cloudNode.path
	val parent: CloudFolderModel
		get() = CloudFolderModel(cloudNode.parent)

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
