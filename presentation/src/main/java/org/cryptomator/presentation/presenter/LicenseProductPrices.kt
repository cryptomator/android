package org.cryptomator.presentation.presenter

import org.cryptomator.presentation.service.ProductPricesCache
import org.cryptomator.presentation.ui.activity.view.LicenseView
import org.cryptomator.util.FlavorConfig

internal fun Presenter<out LicenseView>.loadProductPrices(productPricesCache: ProductPricesCache) {
	if (!FlavorConfig.isFreemiumFlavor) {
		return
	}
	productPricesCache.refreshProductPrices { prices ->
		activity().runOnUiThread {
			if (!isPaused) {
				view?.bindProductPrices(prices)
			}
		}
	}
}
