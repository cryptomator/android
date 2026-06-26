package org.cryptomator.presentation.service

data class ProductInfo(
	val productId: String,
	val price: String,
	val discountPrice: String? = null,
	val discountPercent: Int? = null,
	val discountEndTimeMillis: Long? = null
) {
	companion object {
		const val PRODUCT_FULL_VERSION = "full_version"
		const val PRODUCT_YEARLY_SUBSCRIPTION = "yearly_subscription"
	}
}

data class ProductPrices(
	val subscriptionPrice: String?,
	val lifetimePrice: String?,
	val lifetimeDiscountPrice: String?,
	val lifetimeDiscountPercent: Int?,
	val lifetimeDiscountEndTimeMillis: Long?
)

fun List<ProductInfo>.resolveProductPrices(): ProductPrices {
	val subscription = find { it.productId == ProductInfo.PRODUCT_YEARLY_SUBSCRIPTION }
	val lifetime = find { it.productId == ProductInfo.PRODUCT_FULL_VERSION }
	return ProductPrices(
		subscriptionPrice = subscription?.price,
		lifetimePrice = lifetime?.price,
		lifetimeDiscountPrice = lifetime?.discountPrice,
		lifetimeDiscountPercent = lifetime?.discountPercent,
		lifetimeDiscountEndTimeMillis = lifetime?.discountEndTimeMillis
	)
}
