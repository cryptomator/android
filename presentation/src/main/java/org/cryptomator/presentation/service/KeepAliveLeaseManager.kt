package org.cryptomator.presentation.service

import java.util.concurrent.CopyOnWriteArrayList

import timber.log.Timber

class KeepAliveLeaseManager {

	private val leases = CopyOnWriteArrayList<KeepAliveLease>()

	fun acquire(reason: LeaseReason, ttlMs: Long, hardMaxMs: Long? = null, tag: String): KeepAliveLease {
		val lease = KeepAliveLease(this, reason, ttlMs, hardMaxMs, tag)
		leases.add(lease)
		Timber.tag("KeepAlive").d("Acquired [%s/%s] ttl=%dms hardMax=%s (active=%d)",
			reason, tag, ttlMs, hardMaxMs?.let { "${it}ms" } ?: "none", leases.size)
		return lease
	}

	internal fun removeLease(lease: KeepAliveLease) {
		leases.remove(lease)
		Timber.tag("KeepAlive").d("Released [%s/%s] (active=%d)", lease.reason, lease.tag, leases.size)
	}

	/**
	 * Called by CryptorsService worker every 1 second.
	 * Returns true if lock should be suspended (any non-expired lease exists).
	 */
	fun hasActiveLease(): Boolean {
		val expired = leases.filter { it.isExpired }
		expired.forEach { lease ->
			Timber.tag("KeepAlive").w("Lease [%s/%s] expired", lease.reason, lease.tag)
			lease.release()
		}
		return leases.isNotEmpty()
	}
}
