package org.cryptomator.util

object ByteArrayUtils {

	private const val MAX_ARRAY_LENGTH = Int.MAX_VALUE - 4

	@JvmStatic
	fun join(vararg arrays: ByteArray): ByteArray {
		val joinedLength = computeJoinedLength(*arrays)
		val result = ByteArray(joinedLength)
		var offset = 0
		arrays.forEach { array ->
			System.arraycopy(array, 0, result, offset, array.size)
			offset += array.size
		}
		return result
	}

	private fun computeJoinedLength(vararg arrays: ByteArray): Int {
		var result = 0
		arrays.forEach { array ->
			require(MAX_ARRAY_LENGTH - array.size >= result) { "Overall length of arrays exceeds MAX_ARRAY_LENGTH $MAX_ARRAY_LENGTH" }
			result += array.size
		}
		return result
	}
}
