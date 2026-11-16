package org.cryptomator.data.documents

import android.content.Context
import org.cryptomator.domain.usecases.cloud.DataSource
import org.cryptomator.util.Optional
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.Date

internal const val CONTENT_DIGEST = "SHA-256"

/**
 * A [DataSource] whose streams record their digest once read to the end, see [digestOfCompleteRead], so that an upload
 * can tell what it sent.
 */
internal class DigestingDataSource(private val delegate: DataSource) : DataSource {

	/**
	 * The digest of the latest stream of [open] that was read to its end, or null while none was.
	 */
	@Volatile
	var digestOfCompleteRead: ByteArray? = null
		private set

	override fun size(context: Context): Long? {
		return delegate.size(context)
	}

	override fun open(context: Context): InputStream? {
		val stream = delegate.open(context) ?: return null
		return object : DigestInputStream(stream, MessageDigest.getInstance(CONTENT_DIGEST)) {

			private var complete = false

			override fun read(): Int {
				return super.read().also { if (it < 0) complete() }
			}

			override fun read(b: ByteArray, off: Int, len: Int): Int {
				return super.read(b, off, len).also { if (it < 0) complete() }
			}

			private fun complete() {
				if (!complete) {
					complete = true
					digestOfCompleteRead = messageDigest.digest()
				}
			}
		}
	}

	override fun decorate(delegate: DataSource): DataSource {
		return DigestingDataSource(delegate)
	}

	override fun modifiedDate(context: Context): Optional<Date> {
		return delegate.modifiedDate(context)
	}

	override fun close() {
		delegate.close()
	}
}
