package org.cryptomator.presentation.service

import java.text.DateFormat
import java.time.ZoneOffset
import java.util.Date
import java.util.TimeZone

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

data class SalePromo(
	val discountPercent: Int,
	val endTimeMillis: Long
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

fun ProductPrices.resolveSalePromo(dismissedUntilMillis: Long, nowMillis: Long): SalePromo? {
	val discountPercent = lifetimeDiscountPercent
	val endTimeMillis = lifetimeDiscountEndTimeMillis
	if (discountPercent == null || endTimeMillis == null) {
		return null
	}
	if (nowMillis >= endTimeMillis || nowMillis < dismissedUntilMillis) {
		return null
	}
	return SalePromo(discountPercent, endTimeMillis)
}

fun calculateDiscountPercent(priceMicros: Long, discountPriceMicros: Long): Int? {
	if (priceMicros <= 0 || discountPriceMicros >= priceMicros) {
		return null
	}
	val discountPercent = ((priceMicros - discountPriceMicros) * 100 / priceMicros).toInt()
	return if (discountPercent >= 1) discountPercent else null
}

// The end time is exclusive, so the date of the last millisecond before it is shown.
// Formatting in UTC gives every time zone the same date.
fun formatDiscountEndDate(endTimeMillis: Long): String {
	val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
	dateFormat.timeZone = TimeZone.getTimeZone(ZoneOffset.UTC)
	return dateFormat.format(Date(endTimeMillis - 1))
}
