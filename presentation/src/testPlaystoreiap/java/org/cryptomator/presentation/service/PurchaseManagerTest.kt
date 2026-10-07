package org.cryptomator.presentation.service

import com.android.billingclient.api.Purchase
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class PurchaseManagerTest {

	private val sharedPreferencesHandler: SharedPreferencesHandler = mock()
	private lateinit var purchaseManager: PurchaseManager

	private var acknowledgedTokens = mutableListOf<String>()
	private val acknowledgePurchase: (String) -> Unit = { token -> acknowledgedTokens.add(token) }

	@BeforeEach
	fun setUp() {
		purchaseManager = PurchaseManager(sharedPreferencesHandler)
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("")
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(false)
		acknowledgedTokens.clear()
	}

	// -- LIFETIME --

	@Test
	fun `LIFETIME sets license token when purchase is PURCHASED`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.PURCHASED, "token-1", isAcknowledged = true)

		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler).setLicenseToken("token-1")
		assertFalse(cleared)
	}

	@Test
	fun `LIFETIME skips pending purchase`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.PENDING, "token-1", isAcknowledged = false)

		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler, never()).setLicenseToken(anyString())
		assertFalse(cleared)
	}

	@Test
	fun `LIFETIME clears token when clearIfNotFound and no matching purchase`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("old-token")

		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, emptyList(), clearIfNotFound = true, acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler).setLicenseToken("")
		assertTrue(cleared)
	}

	@Test
	fun `LIFETIME acknowledges unacknowledged purchase`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.PURCHASED, "token-1", isAcknowledged = false)

		purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		assertEquals(listOf("token-1"), acknowledgedTokens)
	}

	@Test
	fun `LIFETIME does not overwrite existing license token`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.PURCHASED, "token-2", isAcknowledged = true)
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("existing-token")

		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler, never()).setLicenseToken(anyString())
		assertFalse(cleared)
	}

	@Test
	fun `LIFETIME returns cleared false when no prior token existed`() {
		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, emptyList(), clearIfNotFound = true, acknowledgePurchase = acknowledgePurchase)

		assertFalse(cleared)
	}

	@Test
	fun `LIFETIME does not arm purchaseRevokedPending`() {
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("old-token")

		purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, emptyList(), clearIfNotFound = true, acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler, never()).setPurchaseRevokedState(anyBoolean(), anyString())
	}

	// -- SUBSCRIPTION --

	@Test
	fun `SUBSCRIPTION sets running subscription when purchase is PURCHASED`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION, Purchase.PurchaseState.PURCHASED, "sub-token", isAcknowledged = true)

		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.SUBSCRIPTION, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler).setHasRunningSubscription(true)
		assertFalse(cleared)
	}

	@Test
	fun `SUBSCRIPTION skips pending subscription`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION, Purchase.PurchaseState.PENDING, "sub-token", isAcknowledged = false)

		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.SUBSCRIPTION, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler, never()).setHasRunningSubscription(anyBoolean())
		assertFalse(cleared)
	}

	@Test
	fun `SUBSCRIPTION clears subscription when clearIfNotFound and no matching purchase`() {
		`when`(sharedPreferencesHandler.hasRunningSubscription()).thenReturn(true)

		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.SUBSCRIPTION, emptyList(), clearIfNotFound = true, acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler).setHasRunningSubscription(false)
		assertTrue(cleared)
	}

	@Test
	fun `SUBSCRIPTION acknowledges unacknowledged subscription`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION, Purchase.PurchaseState.PURCHASED, "sub-token", isAcknowledged = false)

		purchaseManager.handlePurchases(PurchaseManager.Kind.SUBSCRIPTION, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		assertEquals(listOf("sub-token"), acknowledgedTokens)
	}

	@Test
	fun `SUBSCRIPTION returns cleared false when no prior subscription existed`() {
		val cleared = purchaseManager.handlePurchases(PurchaseManager.Kind.SUBSCRIPTION, emptyList(), clearIfNotFound = true, acknowledgePurchase = acknowledgePurchase)

		assertFalse(cleared)
	}

	// -- UNSPECIFIED_STATE handling --

	@Test
	fun `LIFETIME with UNSPECIFIED_STATE does not set token or acknowledge`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.UNSPECIFIED_STATE, "token-1", isAcknowledged = false)

		purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler, never()).setLicenseToken(anyString())
		assertEquals(emptyList<String>(), acknowledgedTokens)
	}

	@Test
	fun `SUBSCRIPTION with UNSPECIFIED_STATE does not set subscription`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION, Purchase.PurchaseState.UNSPECIFIED_STATE, "sub-token", isAcknowledged = false)

		purchaseManager.handlePurchases(PurchaseManager.Kind.SUBSCRIPTION, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler, never()).setHasRunningSubscription(anyBoolean())
	}

	@Test
	fun `LIFETIME clears token when clearIfNotFound and only UNSPECIFIED purchase`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.UNSPECIFIED_STATE, "token-1", isAcknowledged = false)
		`when`(sharedPreferencesHandler.licenseToken()).thenReturn("existing-token")

		purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(purchase), clearIfNotFound = true, acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler).setLicenseToken("")
	}

	// -- Mixed/multiple purchase lists --

	@Test
	fun `LIFETIME skips non-matching product then processes matching PURCHASED`() {
		val otherProduct = mockPurchase("other_product", Purchase.PurchaseState.PURCHASED, "token-other", isAcknowledged = true)
		val fullVersion = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.PURCHASED, "token-full", isAcknowledged = true)

		purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(otherProduct, fullVersion), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler).setLicenseToken("token-full")
	}

	@Test
	fun `LIFETIME continues past PENDING to reach PURCHASED for same product`() {
		val pending = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.PENDING, "token-pending", isAcknowledged = false)
		val purchased = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, Purchase.PurchaseState.PURCHASED, "token-purchased", isAcknowledged = true)

		purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(pending, purchased), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler).setLicenseToken("token-purchased")
	}

	// -- Unknown state --

	@Test
	fun `LIFETIME with unknown state 99 does not set token`() {
		val purchase = mockPurchase(ProductInfo.PRODUCT_FULL_VERSION, 99, "token-1", isAcknowledged = false)

		purchaseManager.handlePurchases(PurchaseManager.Kind.LIFETIME, listOf(purchase), acknowledgePurchase = acknowledgePurchase)

		verify(sharedPreferencesHandler, never()).setLicenseToken(anyString())
		assertEquals(emptyList<String>(), acknowledgedTokens)
	}

	private fun mockPurchase(productId: String, purchaseState: Int, token: String, isAcknowledged: Boolean): Purchase {
		val purchase: Purchase = mock()
		`when`(purchase.products).thenReturn(listOf(productId))
		`when`(purchase.purchaseState).thenReturn(purchaseState)
		`when`(purchase.purchaseToken).thenReturn(token)
		`when`(purchase.isAcknowledged).thenReturn(isAcknowledged)
		`when`(purchase.signature).thenReturn("mock-signature")
		return purchase
	}
}
