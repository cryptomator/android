package org.cryptomator.presentation.model

class SharedFileModel(val id: Any, var fileName: String) : Comparable<SharedFileModel> {

	fun setNewFileName(name: String) {
		fileName = name
	}

	override fun compareTo(other: SharedFileModel): Int {
		val nameComparisonResult = fileName.compareTo(other.fileName)
		return if (nameComparisonResult == 0) {
			hashCode() - other.hashCode()
		} else {
			nameComparisonResult
		}
	}

	override fun hashCode(): Int {
		return id.hashCode()
	}

	override fun equals(other: Any?): Boolean {
		return (other === this //
				|| (other != null //
				&& javaClass == other.javaClass //
				&& internalEquals(other as SharedFileModel)))
	}

	private fun internalEquals(obj: SharedFileModel): Boolean {
		return id == obj.id
	}
}
