package org.cryptomator.presentation.service

import java.util.concurrent.TimeUnit

class ProductPricesCache(
	private val queryProductDetails: ((List<ProductInfo>) -> Unit) -> Unit,
	private val currentTimeMillis: () -> Long
) {

	@Volatile
	private var cachedEntry: Entry? = null

	fun queryProductPrices(callback: (ProductPrices) -> Unit) {
		val entry = cachedEntry
		if (entry != null && currentTimeMillis() - entry.loadedAtMillis < CACHE_DURATION_MILLIS) {
			callback(entry.prices)
			return
		}
		refreshProductPrices { prices -> callback(cachedEntry?.prices ?: prices) }
	}

	fun refreshProductPrices(callback: (ProductPrices) -> Unit) {
		queryProductDetails { products ->
			val prices = products.resolveProductPrices()
			if (prices.lifetimePrice != null) {
				cachedEntry = Entry(prices, currentTimeMillis())
			}
			callback(prices)
		}
	}

	private class Entry(val prices: ProductPrices, val loadedAtMillis: Long)

	companion object {

		private val CACHE_DURATION_MILLIS = TimeUnit.HOURS.toMillis(1)
	}
}
