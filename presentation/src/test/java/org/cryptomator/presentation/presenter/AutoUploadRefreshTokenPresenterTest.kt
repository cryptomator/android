package org.cryptomator.presentation.presenter

import android.app.Activity
import org.cryptomator.presentation.CryptomatorApp
import org.cryptomator.presentation.exception.ExceptionHandlers
import org.cryptomator.presentation.ui.activity.view.AutoUploadRefreshTokenView
import org.cryptomator.presentation.workflow.ActivityResult
import org.cryptomator.presentation.workflow.AuthenticationExceptionHandler
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class AutoUploadRefreshTokenPresenterTest {

	private val app = mock<CryptomatorApp>()
	private val view = mock<AutoUploadRefreshTokenView>()

	private lateinit var inTest: AutoUploadRefreshTokenPresenter

	@BeforeEach
	fun setup() {
		val activity = mock<Activity>()
		whenever(activity.application).thenReturn(app)
		whenever(view.activity()).thenReturn(activity)
		inTest = AutoUploadRefreshTokenPresenter(mock<ExceptionHandlers>(), mock<AuthenticationExceptionHandler>())
		inTest.view = view
	}

	@Test
	fun testUploadRestartsThroughTheVaultAfterAuthentication() {
		inTest.onAutoUploadCloudAuthenticated(ActivityResult(null, null, true))

		// uploading to the authenticated cloud directly would skip the vault's encryption
		verify(app).startAutoUpload()
		verify(app, never()).startAutoUpload(any())
		verify(view).finish()
	}

	@Test
	fun testUploadDoesntRestartWithoutAuthentication() {
		inTest.onAutoUploadCloudAuthenticated(ActivityResult(null, null, false))

		verify(app, never()).startAutoUpload()
		verify(app, never()).startAutoUpload(any())
		verify(view).finish()
	}
}
