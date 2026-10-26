package org.cryptomator.presentation.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class ProductPricesCacheTest {

	private var nowMillis = 1_700_000_000_000
	private var productDetails = listOf(lifetimeProduct(discountPercent = 33))
	private var productDetailsQueries = 0

	private val inTest = ProductPricesCache(
		queryProductDetails = { callback ->
			productDetailsQueries++
			callback(productDetails)
		},
		currentTimeMillis = { nowMillis }
	)

	private fun lifetimeProduct(discountPercent: Int): ProductInfo {
		return ProductInfo(ProductInfo.PRODUCT_FULL_VERSION, "$49.99", "$32.99", discountPercent, nowMillis + TimeUnit.DAYS.toMillis(1))
	}

	private fun subscriptionProduct(): ProductInfo {
		return ProductInfo(ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION, "$9.99/yr")
	}

	private fun queryProductPrices(): ProductPrices {
		var result: ProductPrices? = null
		inTest.queryProductPrices { result = it }
		return result!!
	}

	private fun refreshProductPrices(): ProductPrices {
		var result: ProductPrices? = null
		inTest.refreshProductPrices { result = it }
		return result!!
	}

	@Test
	fun `queryProductPrices loads product details on first call`() {
		val prices = queryProductPrices()

		assertEquals(33, prices.lifetimeDiscountPercent)
		assertEquals(1, productDetailsQueries)
	}

	@Test
	fun `queryProductPrices serves cached prices within an hour`() {
		queryProductPrices()
		productDetails = listOf(lifetimeProduct(discountPercent = 50))
		nowMillis += TimeUnit.MINUTES.toMillis(59)

		val prices = queryProductPrices()

		assertEquals(33, prices.lifetimeDiscountPercent)
		assertEquals(1, productDetailsQueries)
	}

	@Test
	fun `queryProductPrices reloads product details after an hour`() {
		queryProductPrices()
		productDetails = listOf(lifetimeProduct(discountPercent = 50))
		nowMillis += TimeUnit.HOURS.toMillis(1)

		val prices = queryProductPrices()

		assertEquals(50, prices.lifetimeDiscountPercent)
		assertEquals(2, productDetailsQueries)
	}

	@Test
	fun `queryProductPrices does not cache failed queries`() {
		productDetails = emptyList()
		val failedPrices = queryProductPrices()
		productDetails = listOf(lifetimeProduct(discountPercent = 33))

		val prices = queryProductPrices()

		assertNull(failedPrices.lifetimeDiscountPercent)
		assertEquals(33, prices.lifetimeDiscountPercent)
		assertEquals(2, productDetailsQueries)
	}

	@Test
	fun `queryProductPrices keeps last loaded prices when reload fails`() {
		queryProductPrices()
		productDetails = emptyList()
		nowMillis += TimeUnit.HOURS.toMillis(1)

		val prices = queryProductPrices()

		assertEquals(33, prices.lifetimeDiscountPercent)
		assertEquals(2, productDetailsQueries)
	}

	@Test
	fun `queryProductPrices caches prices without running discount`() {
		productDetails = listOf(ProductInfo(ProductInfo.PRODUCT_FULL_VERSION, "$49.99"))
		queryProductPrices()

		val prices = queryProductPrices()

		assertEquals("$49.99", prices.lifetimePrice)
		assertEquals(1, productDetailsQueries)
	}

	@Test
	fun `queryProductPrices does not cache results without lifetime product`() {
		productDetails = listOf(subscriptionProduct())
		queryProductPrices()
		productDetails = listOf(lifetimeProduct(discountPercent = 33), subscriptionProduct())

		val prices = queryProductPrices()

		assertEquals(33, prices.lifetimeDiscountPercent)
		assertEquals(2, productDetailsQueries)
	}

	@Test
	fun `queryProductPrices keeps last loaded prices when reload misses lifetime product`() {
		queryProductPrices()
		productDetails = listOf(subscriptionProduct())
		nowMillis += TimeUnit.HOURS.toMillis(1)

		val prices = queryProductPrices()

		assertEquals(33, prices.lifetimeDiscountPercent)
		assertEquals(2, productDetailsQueries)
	}

	@Test
	fun `refreshProductPrices loads product details within an hour and updates cache`() {
		queryProductPrices()
		productDetails = listOf(lifetimeProduct(discountPercent = 50))

		val refreshedPrices = refreshProductPrices()
		val cachedPrices = queryProductPrices()

		assertEquals(50, refreshedPrices.lifetimeDiscountPercent)
		assertEquals(50, cachedPrices.lifetimeDiscountPercent)
		assertEquals(2, productDetailsQueries)
	}

	@Test
	fun `refreshProductPrices returns failed result instead of cached prices`() {
		queryProductPrices()
		productDetails = emptyList()

		val prices = refreshProductPrices()

		assertNull(prices.lifetimeDiscountPercent)
	}
}
