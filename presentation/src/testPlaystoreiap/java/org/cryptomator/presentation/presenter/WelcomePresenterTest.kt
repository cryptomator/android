package org.cryptomator.presentation.presenter

import android.app.Activity
import org.cryptomator.domain.usecases.DoLicenseCheckUseCase
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.service.ProductPrices
import org.cryptomator.presentation.service.ProductPricesCache
import org.cryptomator.presentation.ui.activity.view.WelcomeView
import org.cryptomator.util.SharedPreferencesHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any

class WelcomePresenterTest {

	private val welcomeView: WelcomeView = Mockito.mock(WelcomeView::class.java)
	private val activity: Activity = Mockito.mock(Activity::class.java)
	private val productPricesCache: ProductPricesCache = Mockito.mock(ProductPricesCache::class.java)
	private val prices = ProductPrices("$9.99/yr", "$49.99", "$32.99", 33, 1_700_000_000_000)

	private lateinit var inTest: WelcomePresenter

	@BeforeEach
	fun setup() {
		inTest = WelcomePresenter(
			Mockito.mock(ExceptionHandlers::class.java),
			Mockito.mock(DoLicenseCheckUseCase::class.java),
			Mockito.mock(SharedPreferencesHandler::class.java),
			productPricesCache
		)
		Mockito.doReturn(activity).`when`(welcomeView).activity()
		Mockito.doAnswer { invocation ->
			invocation.getArgument<Runnable>(0).run()
		}.`when`(activity).runOnUiThread(any())
		Mockito.doAnswer { invocation ->
			invocation.getArgument<(ProductPrices) -> Unit>(0)(prices)
		}.`when`(productPricesCache).refreshProductPrices(any())
		inTest.view = welcomeView
	}

	@Test
	fun `onLicenseViewReady loads and binds product prices`() {
		inTest.onLicenseViewReady()

		Mockito.verify(productPricesCache).refreshProductPrices(any())
		Mockito.verify(welcomeView).bindProductPrices(prices)
	}

	@Test
	fun `resumed does not load product prices without license view`() {
		Mockito.doReturn(false).`when`(welcomeView).hasLicenseView()

		inTest.resumed()

		Mockito.verify(productPricesCache, Mockito.never()).refreshProductPrices(any())
	}

	@Test
	fun `resumed loads and binds product prices when license view exists`() {
		Mockito.doReturn(true).`when`(welcomeView).hasLicenseView()

		inTest.resumed()

		Mockito.verify(productPricesCache).refreshProductPrices(any())
		Mockito.verify(welcomeView).bindProductPrices(prices)
	}

	@Test
	fun `onLicenseViewReady ignores product prices that reach the UI thread after pause`() {
		var pendingRunnable: Runnable? = null
		Mockito.doAnswer { invocation ->
			pendingRunnable = invocation.getArgument(0)
			null
		}.`when`(activity).runOnUiThread(any())

		inTest.onLicenseViewReady()
		inTest.pause()
		pendingRunnable!!.run()

		Mockito.verify(welcomeView, Mockito.never()).bindProductPrices(any())
	}
}
