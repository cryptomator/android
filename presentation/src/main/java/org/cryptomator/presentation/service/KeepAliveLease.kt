package org.cryptomator.presentation.service

import android.os.SystemClock
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

class KeepAliveLease internal constructor(
	private val manager: KeepAliveLeaseManager,
	val reason: LeaseReason,
	private val defaultTtlMs: Long,
	private val hardMaxMs: Long?,
	val tag: String
) : Closeable {

	private val released = AtomicBoolean(false)
	private val createdAt = SystemClock.elapsedRealtime()

	@Volatile
	var expiresAt = createdAt + defaultTtlMs
		private set

	fun release() {
		if (released.compareAndSet(false, true)) {
			manager.removeLease(this)
		}
	}

	override fun close() = release()

	@JvmOverloads
	fun renew(ttlMs: Long = defaultTtlMs) {
		if (released.get()) return
		val now = SystemClock.elapsedRealtime()
		val cap = hardMaxMs?.let { createdAt + it } ?: Long.MAX_VALUE
		expiresAt = minOf(now + ttlMs, cap)
	}

	val isExpired: Boolean
		get() = !released.get() && SystemClock.elapsedRealtime() > expiresAt

	val isReleased: Boolean get() = released.get()
}

enum class LeaseReason { FILE_PICKER, FOLDER_PICKER, UPLOAD, EDITING }
