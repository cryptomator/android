package org.cryptomator.presentation.ui.activity.view

import org.cryptomator.presentation.service.ProductPrices

interface LicenseView : View {
	fun showConfirmationDialog(mail: String)
	fun bindProductPrices(prices: ProductPrices)
}
