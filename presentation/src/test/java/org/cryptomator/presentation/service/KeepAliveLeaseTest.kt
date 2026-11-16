package org.cryptomator.presentation.service

import android.os.SystemClock

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic

class KeepAliveLeaseTest {

	private lateinit var manager: KeepAliveLeaseManager
	private lateinit var systemClockMock: MockedStatic<SystemClock>
	private var now = 10_000L

	@BeforeEach
	fun setup() {
		manager = KeepAliveLeaseManager()
		systemClockMock = mockStatic(SystemClock::class.java)
		systemClockMock.`when`<Long> { SystemClock.elapsedRealtime() }.thenAnswer { now }
	}

	@AfterEach
	fun tearDown() {
		systemClockMock.close()
	}

	@Test
	fun releaseRemovesLease() {
		val lease = manager.acquire(LeaseReason.FILE_PICKER, 60_000L, tag = "test")
		assertThat(manager.hasActiveLease(), `is`(true))

		lease.release()
		assertThat(manager.hasActiveLease(), `is`(false))
	}

	@Test
	fun doubleReleaseIsNoOp() {
		val lease = manager.acquire(LeaseReason.FILE_PICKER, 60_000L, tag = "test")

		lease.release()
		lease.release()

		assertThat(lease.isReleased, `is`(true))
		assertThat(manager.hasActiveLease(), `is`(false))
	}

	@Test
	fun expiredLeaseDetectedByIsExpired() {
		val lease = manager.acquire(LeaseReason.UPLOAD, 5_000L, tag = "test")
		assertThat(lease.isExpired, `is`(false))

		now += 6_000L
		assertThat(lease.isExpired, `is`(true))
	}

	@Test
	fun hasActiveLeaseAutoExpiresStaleLeases() {
		manager.acquire(LeaseReason.UPLOAD, 5_000L, tag = "stale")
		assertThat(manager.hasActiveLease(), `is`(true))

		now += 6_000L
		assertThat(manager.hasActiveLease(), `is`(false))
	}

	@Test
	fun renewExtendsTtl() {
		val lease = manager.acquire(LeaseReason.UPLOAD, 5_000L, tag = "test")

		now += 4_000L
		lease.renew()

		now += 4_000L
		assertThat(lease.isExpired, `is`(false))

		now += 2_000L
		assertThat(lease.isExpired, `is`(true))
	}

	@Test
	fun renewBoundedByHardMax() {
		val lease = manager.acquire(LeaseReason.UPLOAD, 5_000L, hardMaxMs = 8_000L, tag = "test")

		now += 4_000L
		lease.renew()

		// hard max is createdAt + 8_000 = 18_000; renew would set to now + 5_000 = 19_000
		// should be capped at 18_000
		assertThat(lease.expiresAt, `is`(18_000L))
	}

	@Test
	fun renewAfterReleaseIsNoOp() {
		val lease = manager.acquire(LeaseReason.UPLOAD, 5_000L, tag = "test")
		val originalExpiry = lease.expiresAt

		lease.release()
		now += 1_000L
		lease.renew()

		assertThat(lease.expiresAt, `is`(originalExpiry))
	}

	@Test
	fun multipleLeases() {
		val lease1 = manager.acquire(LeaseReason.FILE_PICKER, 3_000L, tag = "picker")
		val lease2 = manager.acquire(LeaseReason.UPLOAD, 10_000L, tag = "upload")

		assertThat(manager.hasActiveLease(), `is`(true))

		lease1.release()
		assertThat(manager.hasActiveLease(), `is`(true))

		lease2.release()
		assertThat(manager.hasActiveLease(), `is`(false))
	}

	@Test
	fun overlappingLeasesAreIndependent() {
		val lease1 = manager.acquire(LeaseReason.EDITING, 10_000L, tag = "editing")
		val lease2 = manager.acquire(LeaseReason.UPLOAD, 5_000L, tag = "upload")

		// Upload expires but editing is still active
		now += 6_000L
		assertThat(manager.hasActiveLease(), `is`(true))
		assertThat(lease2.isReleased, `is`(true))
		assertThat(lease1.isReleased, `is`(false))
	}

	@Test
	fun concurrentReleaseFromMultipleThreads() {
		val lease = manager.acquire(LeaseReason.FILE_PICKER, 60_000L, tag = "test")

		val threads = (1..10).map {
			Thread { lease.release() }
		}
		threads.forEach { it.start() }
		threads.forEach { it.join() }

		assertThat(lease.isReleased, `is`(true))
		assertThat(manager.hasActiveLease(), `is`(false))
	}
}
