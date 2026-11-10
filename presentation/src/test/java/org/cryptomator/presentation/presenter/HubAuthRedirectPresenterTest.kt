package org.cryptomator.presentation.presenter

import android.net.Uri
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.HubAuthRedirectView
import org.cryptomator.presentation.util.HubAuthRedirects
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class HubAuthRedirectPresenterTest {

	private val hubAuthRedirects = mock<HubAuthRedirects>()
	private val view = mock<HubAuthRedirectView>()

	private lateinit var inTest: HubAuthRedirectPresenter

	@BeforeEach
	fun setup() {
		inTest = HubAuthRedirectPresenter(mock<ExceptionHandlers>(), hubAuthRedirects)
		inTest.view = view
	}

	@Test
	fun testRedirectIsHandedToTheWaitingUnlock() {
		val redirectUri = mock<Uri>()

		inTest.onRedirect(redirectUri)

		verify(hubAuthRedirects).receiveRedirect(redirectUri)
		verify(view).finish()
	}
}
