package org.cryptomator.data.cloud.box

import java.security.MessageDigest
import java.util.Base64

internal object BoxDigest {

	fun sha1(): MessageDigest {
		return MessageDigest.getInstance("SHA-1")
	}

	fun sha1Base64(data: ByteArray, length: Int): String {
		val digest = sha1()
		digest.update(data, 0, length)
		return base64(digest)
	}

	fun base64(digest: MessageDigest): String {
		return Base64.getEncoder().encodeToString(digest.digest())
	}
}
