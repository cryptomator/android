package org.cryptomator.presentation.model

import org.cryptomator.domain.Cloud
import java.io.Serializable

abstract class CloudModel internal constructor(private val cloud: Cloud) : Serializable {

	abstract fun name(): Int
	abstract fun username(): String?
	abstract fun cloudType(): CloudTypeModel

	fun toCloud(): Cloud {
		return cloud
	}

	override fun equals(other: Any?): Boolean {
		if (other === this) return true
		return if (other == null || javaClass != other.javaClass) false else internalEquals(other as CloudModel)
	}

	private fun internalEquals(o: CloudModel): Boolean {
		return cloud == o.cloud
	}

	override fun hashCode(): Int {
		return cloud.hashCode()
	}

}
