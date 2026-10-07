package org.cryptomator.presentation.service

import android.app.Activity
import android.widget.Toast
import org.cryptomator.presentation.R
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PurchaseRevokedToastObserverTest {

	private val sharedPreferencesHandler: SharedPreferencesHandler = mock()
	private val activity: Activity = mock()
	private lateinit var observer: PurchaseRevokedToastObserver

	@BeforeEach
	fun setUp() {
		observer = PurchaseRevokedToastObserver(sharedPreferencesHandler)
	}

	@Test
	fun `onActivityResumed with pending flag and LIFETIME_REFUNDED reason shows refund toast and clears state`() {
		`when`(sharedPreferencesHandler.purchaseRevokedPending()).thenReturn(true)
		`when`(sharedPreferencesHandler.purchaseRevokedReason()).thenReturn(PurchaseRevokedReason.LIFETIME_REFUNDED.name)

		mockStatic(Toast::class.java).use { toastMock ->
			val toast: Toast = mock()
			toastMock.`when`<Toast> { Toast.makeText(activity, R.string.toast_purchase_revoked_refunded, Toast.LENGTH_LONG) }.thenReturn(toast)

			observer.onActivityResumed(activity)

			verify(toast).show()
			verify(sharedPreferencesHandler).clearPurchaseRevokedState()
		}
	}

	@Test
	fun `onActivityResumed with pending flag and SUBSCRIPTION_INACTIVE reason shows sub-inactive toast`() {
		`when`(sharedPreferencesHandler.purchaseRevokedPending()).thenReturn(true)
		`when`(sharedPreferencesHandler.purchaseRevokedReason()).thenReturn(PurchaseRevokedReason.SUBSCRIPTION_INACTIVE.name)

		mockStatic(Toast::class.java).use { toastMock ->
			val toast: Toast = mock()
			toastMock.`when`<Toast> { Toast.makeText(activity, R.string.toast_purchase_revoked_subscription_inactive, Toast.LENGTH_LONG) }.thenReturn(toast)

			observer.onActivityResumed(activity)

			verify(toast).show()
			verify(sharedPreferencesHandler).clearPurchaseRevokedState()
		}
	}

	@Test
	fun `onActivityResumed without pending flag does nothing`() {
		`when`(sharedPreferencesHandler.purchaseRevokedPending()).thenReturn(false)

		observer.onActivityResumed(activity)

		verify(sharedPreferencesHandler, never()).purchaseRevokedReason()
		verify(sharedPreferencesHandler, never()).clearPurchaseRevokedState()
	}

	@Test
	fun `onActivityResumed with invalid reason string clears state but shows no toast`() {
		`when`(sharedPreferencesHandler.purchaseRevokedPending()).thenReturn(true)
		`when`(sharedPreferencesHandler.purchaseRevokedReason()).thenReturn("BOGUS_REASON")

		mockStatic(Toast::class.java).use { toastMock ->
			observer.onActivityResumed(activity)

			toastMock.verifyNoInteractions()
			verify(sharedPreferencesHandler).clearPurchaseRevokedState()
		}
	}
}
