package org.cryptomator.data.cloud.crypto

import org.cryptomator.cryptolib.api.Masterkey
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

/**
 * Encodes masterkeys as human-readable recovery keys compatible with Cryptomator for desktop.
 */
internal object RecoveryKeyFactory {

	private const val WORD_LIST = "/recoverykey/4096words_en.txt"
	private const val WORD_COUNT = 4096
	private const val DELIMITER = " "

	private val words: List<String> by lazy {
		val wordList = RecoveryKeyFactory::class.java.getResourceAsStream(WORD_LIST) ?: throw IllegalStateException("Missing word list $WORD_LIST")
		wordList.bufferedReader(StandardCharsets.US_ASCII).useLines { lines ->
			lines.take(WORD_COUNT).toList().also { check(it.size == WORD_COUNT) { "Word list $WORD_LIST has fewer than $WORD_COUNT words" } }
		}
	}

	/**
	 * Appends the two low-order bytes of the masterkey's CRC32 checksum to the raw masterkey and encodes every three bytes
	 * of the result as two words of 12 bits each.
	 *
	 * @return the recovery key, a space-separated list of 44 words
	 */
	fun createRecoveryKey(masterkey: Masterkey): String {
		// not wiped afterwards: cryptolib hands out the key's backing array, not a copy
		val rawKey = masterkey.encoded
		val checksum = CRC32().apply { update(rawKey) }.value
		val paddedKey = rawKey.copyOf(rawKey.size + 2)
		paddedKey[rawKey.size] = checksum.toByte()
		paddedKey[rawKey.size + 1] = (checksum shr 8).toByte()
		try {
			return (paddedKey.indices step 3).flatMap { i ->
				val b1 = paddedKey[i].toInt()
				val b2 = paddedKey[i + 1].toInt()
				val b3 = paddedKey[i + 2].toInt()
				listOf(
					words[(0xFF0 and (b1 shl 4)) + (0x00F and (b2 shr 4))], //
					words[(0xF00 and (b2 shl 8)) + (0x0FF and b3)]
				)
			}.joinToString(DELIMITER)
		} finally {
			paddedKey.fill(0)
		}
	}
}
