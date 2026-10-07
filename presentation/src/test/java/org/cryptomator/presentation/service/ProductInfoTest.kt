package org.cryptomator.presentation.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Locale
import java.util.TimeZone

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
		assertNull(prices.lifetimeDiscountPercent)
		assertNull(prices.lifetimeDiscountEndTimeMillis)
	}

	@Test
	fun `resolveProductPrices returns null lifetimeDiscountPrice when lifetime missing`() {
		val prices = emptyList<ProductInfo>().resolveProductPrices()

		assertNull(prices.lifetimeDiscountPrice)
		assertNull(prices.lifetimeDiscountPercent)
		assertNull(prices.lifetimeDiscountEndTimeMillis)
	}

	private fun discountedPrices(percent: Int? = 33, endTimeMillis: Long? = SALE_END): ProductPrices {
		return ProductPrices("$9.99/yr", "$49.99", "$32.99", percent, endTimeMillis)
	}

	@Test
	fun `resolveSalePromo returns promo while discount is running`() {
		val salePromo = discountedPrices().resolveSalePromo(dismissedUntilMillis = 0, nowMillis = SALE_END - 1)

		assertEquals(SalePromo(33, SALE_END), salePromo)
	}

	@Test
	fun `resolveSalePromo returns null when discount lacks percent or end time`() {
		assertNull(ProductPrices("$9.99/yr", "$49.99", null, null, null).resolveSalePromo(dismissedUntilMillis = 0, nowMillis = SALE_END - 1))
		assertNull(discountedPrices(percent = null).resolveSalePromo(dismissedUntilMillis = 0, nowMillis = SALE_END - 1))
		assertNull(discountedPrices(endTimeMillis = null).resolveSalePromo(dismissedUntilMillis = 0, nowMillis = SALE_END - 1))
	}

	@Test
	fun `resolveSalePromo returns null when discount has ended`() {
		assertNull(discountedPrices().resolveSalePromo(dismissedUntilMillis = 0, nowMillis = SALE_END))
	}

	@Test
	fun `resolveSalePromo returns null while dismissed sale is still running`() {
		assertNull(discountedPrices().resolveSalePromo(dismissedUntilMillis = SALE_END, nowMillis = SALE_END - 1))
	}

	@Test
	fun `resolveSalePromo returns promo for next sale after a dismissed one has ended`() {
		val nextSaleEnd = SALE_END + 1_000_000
		val prices = discountedPrices(percent = 50, endTimeMillis = nextSaleEnd)

		val salePromo = prices.resolveSalePromo(dismissedUntilMillis = SALE_END, nowMillis = SALE_END + 1)

		assertEquals(SalePromo(50, nextSaleEnd), salePromo)
	}

	@Test
	fun `calculateDiscountPercent rounds down to never overstate the discount`() {
		assertEquals(33, calculateDiscountPercent(priceMicros = 10_000_000, discountPriceMicros = 6_640_000))
	}

	@Test
	fun `calculateDiscountPercent returns null when discount is below one percent`() {
		assertNull(calculateDiscountPercent(priceMicros = 1_000, discountPriceMicros = 999))
	}

	@Test
	fun `calculateDiscountPercent returns null when discount price is not lower`() {
		assertNull(calculateDiscountPercent(priceMicros = 29_990_000, discountPriceMicros = 29_990_000))
	}

	@Test
	fun `formatDiscountEndDate shows last full day in UTC for an exclusive midnight end`() {
		val defaultLocale = Locale.getDefault()
		val defaultTimeZone = TimeZone.getDefault()
		try {
			Locale.setDefault(Locale.US)
			TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))

			assertEquals("Oct 31, 2026", formatDiscountEndDate(NOVEMBER_FIRST_2026_MIDNIGHT_UTC))
		} finally {
			Locale.setDefault(defaultLocale)
			TimeZone.setDefault(defaultTimeZone)
		}
	}

	companion object {

		private const val SALE_END = 1_700_000_000_000
		private const val NOVEMBER_FIRST_2026_MIDNIGHT_UTC = 1_793_491_200_000
	}
}
