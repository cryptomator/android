package org.cryptomator.presentation.service

data class ProductInfo(
	val productId: String,
	val formattedPrice: String
) {
	companion object {
		const val PRODUCT_FULL_VERSION = "full_version"
		const val PRODUCT_YEARLY_SUBSCRIPTION = "yearly_subscription"
	}
}

data class ProductPrices(
	val subscriptionPrice: String?,
	val lifetimePrice: String?
)

/**
 * Resolves formatted prices for the known yearly subscription and full-version products from the list.
 *
 * @receiver The list of ProductInfo entries to search.
 * @return A ProductPrices containing `subscriptionPrice` (formatted price for PRODUCT_YEARLY_SUBSCRIPTION) and `lifetimePrice` (formatted price for PRODUCT_FULL_VERSION); each is `null` if the corresponding product is not present.
 */
fun List<ProductInfo>.resolveProductPrices(): ProductPrices {
	val subscription = find { it.productId == ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION }
	val lifetime = find { it.productId == ProductInfo.PRODUCT_FULL_VERSION }
	return ProductPrices(
		subscriptionPrice = subscription?.formattedPrice,
		lifetimePrice = lifetime?.formattedPrice
	)
}
