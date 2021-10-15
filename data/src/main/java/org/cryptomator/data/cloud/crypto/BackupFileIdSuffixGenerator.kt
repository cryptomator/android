package org.cryptomator.data.cloud.crypto

import com.google.common.io.BaseEncoding
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * Utility class for generating a suffix for the backup file to make it unique to its original master key file.
 */
internal object BackupFileIdSuffixGenerator {

	/**
	 * Computes the SHA-256 digest of the given byte array and returns a file suffix containing the first 4 bytes in hex string format.
	 *
	 * @param fileBytes the input byte for which the digest is computed
	 * @return "." + first 4 bytes of SHA-256 digest in hex string format
	 */
	@JvmStatic
	fun generate(fileBytes: ByteArray): String {
		return try {
			val md = MessageDigest.getInstance("SHA-256")
			val digest = md.digest(fileBytes)
			"." + BaseEncoding.base16().encode(digest, 0, 4)
		} catch (e: NoSuchAlgorithmException) {
			throw IllegalStateException("Every Java Platform must support the Message Digest algorithm SHA-256", e)
		}
	}
}
