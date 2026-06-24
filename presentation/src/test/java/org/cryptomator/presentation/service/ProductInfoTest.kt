package org.cryptomator.presentation.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProductInfoTest {

	@Test
	fun `resolveProductPrices returns both prices when both products present`() {
		val products = listOf(
			ProductInfo(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION, "$9.99/yr"),
			ProductInfo(ProductInfo.PRODUCT_FULL_VERSION, "$49.99")
		)

		val prices = products.resolveProductPrices()

		assertEquals("$9.99/yr", prices.subscriptionPrice)
		assertEquals("$49.99", prices.lifetimePrice)
	}

	@Test
	fun `resolveProductPrices returns null for missing subscription`() {
		val products = listOf(
			ProductInfo(ProductInfo.PRODUCT_FULL_VERSION, "$49.99")
		)

		val prices = products.resolveProductPrices()

		assertNull(prices.subscriptionPrice)
		assertEquals("$49.99", prices.lifetimePrice)
	}

	@Test
	fun `resolveProductPrices returns null for missing lifetime`() {
		val products = listOf(
			ProductInfo(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION, "$9.99/yr")
		)

		val prices = products.resolveProductPrices()

		assertEquals("$9.99/yr", prices.subscriptionPrice)
		assertNull(prices.lifetimePrice)
	}

	@Test
	fun `resolveProductPrices returns both null for empty list`() {
		val prices = emptyList<ProductInfo>().resolveProductPrices()

		assertNull(prices.subscriptionPrice)
		assertNull(prices.lifetimePrice)
	}

	@Test
	fun `resolveProductPrices returns both null for unrelated products`() {
		val products = listOf(
			ProductInfo("some_other_product", "$1.99")
		)

		val prices = products.resolveProductPrices()

		assertNull(prices.subscriptionPrice)
		assertNull(prices.lifetimePrice)
	}

	@Test
	fun `resolveProductPrices returns lifetime discount details when lifetime has discount`() {
		val products = listOf(
			ProductInfo(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION, "$9.99/yr"),
			ProductInfo(ProductInfo.PRODUCT_FULL_VERSION, "$49.99", "$24.99", 50, 1_700_000_000_000)
		)

		val prices = products.resolveProductPrices()

		assertEquals("$49.99", prices.lifetimePrice)
		assertEquals("$24.99", prices.lifetimeDiscountPrice)
		assertEquals(50, prices.lifetimeDiscountPercent)
		assertEquals(1_700_000_000_000, prices.lifetimeDiscountEndTimeMillis)
	}

	@Test
	fun `resolveProductPrices returns null lifetimeDiscountPrice when no discount`() {
		val products = listOf(
			ProductInfo(ProductInfo.PRODUCT_FULL_VERSION, "$49.99")
		)

		val prices = products.resolveProductPrices()

		assertEquals("$49.99", prices.lifetimePrice)
		assertNull(prices.lifetimeDiscountPrice)
	}

	@Test
	fun `resolveProductPrices returns null lifetimeDiscountPrice when lifetime missing`() {
		val prices = emptyList<ProductInfo>().resolveProductPrices()

		assertNull(prices.lifetimeDiscountPrice)
	}
}
