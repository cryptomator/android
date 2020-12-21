package org.cryptomator.util.file

import org.cryptomator.util.Function

class MimeType internal constructor(val mediatype: String, val subtype: String) {

	fun combine(other: MimeType): MimeType {
		return when {
			equals(other) -> {
				this
			}
			hasMatchingMediatype(other) -> {
				MimeType(mediatype, WILDCARD_SUBTYPE)
			}
			else -> {
				WILDCARD_MIME_TYPE
			}
		}
	}

	fun hasMatchingMediatype(other: MimeType): Boolean {
		return mediatype == other.mediatype
	}

	override fun equals(other: Any?): Boolean {
		if (other === this) return true
		return if (other == null || javaClass != other.javaClass) false else internalEquals(other as MimeType)
	}

	private fun internalEquals(o: MimeType): Boolean {
		return mediatype == o.mediatype && subtype == o.subtype
	}

	override fun hashCode(): Int {
		val prime = 31
		var hash = -1719400763
		hash = hash * prime + mediatype.hashCode()
		hash = hash * prime + subtype.hashCode()
		return hash
	}

	override fun toString(): String {
		return "$mediatype/$subtype"
	}

	companion object {
		val TO_STRING = Function { obj: MimeType -> obj.toString() }
		const val WILDCARD_MEDIATYPE = "*"
		const val WILDCARD_SUBTYPE = "*"

		@JvmField
		val APPLICATION_OCTET_STREAM = MimeType("application", "octet-stream")
		val WILDCARD_MIME_TYPE = MimeType(WILDCARD_MEDIATYPE, WILDCARD_SUBTYPE)
	}
}
