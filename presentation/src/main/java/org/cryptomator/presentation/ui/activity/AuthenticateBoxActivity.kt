package org.cryptomator.presentation.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.widget.Toast
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.databinding.ActivityLayoutBinding
import org.cryptomator.presentation.presenter.CloudConnectionListPresenter
import java.util.UUID
import timber.log.Timber

@Activity
class AuthenticateBoxActivity : BaseActivity<ActivityLayoutBinding>(ActivityLayoutBinding::inflate) {

	private val startAuthenticationRequestCode = 1233
	private val redirectTimeoutAfterAuthenticationAndResumed = 1000L

	private var cancelAuthenticationHandler: Handler = Handler()
	private var oAuthResultReceived = false
	private var oAuthState: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		oAuthState = savedInstanceState?.getString(OAUTH_STATE)
		super.onCreate(savedInstanceState)
	}

	override fun setupView() {
		if (intent.data != null) {
			// the redirect arrived after this activity had already finished, e.g. due to the timeout, so the flow it belongs to is gone
			handleRedirect(intent)
			return
		}
		if (oAuthState != null) {
			return // authentication already started before the activity got recreated
		}
		val state = UUID.randomUUID().toString()
		oAuthState = state
		val uri = Uri.parse("https://account.box.com/api/oauth2/authorize")
			.buildUpon()
			.appendQueryParameter("response_type", "code")
			.appendQueryParameter("client_id", BuildConfig.BOX_CLIENT_ID)
			.appendQueryParameter("redirect_uri", "$REDIRECT_SCHEME_PREFIX${BuildConfig.BOX_CLIENT_ID}://$REDIRECT_HOST")
			.appendQueryParameter("state", state)
			.build()

		startActivityForResult(Intent(Intent.ACTION_VIEW, uri), startAuthenticationRequestCode)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(OAUTH_STATE, oAuthState)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)
		if (requestCode == startAuthenticationRequestCode) {
			cancelAuthenticationHandler.postDelayed({
				if (!oAuthResultReceived) {
					Timber.tag("AuthenticateBoxActivity").i("Authentication canceled or no redirect received after resuming Cryptomator since 1s")
					Toast.makeText(context(), R.string.error_authentication_failed, Toast.LENGTH_SHORT).show()
					finish()
				}
			}, redirectTimeoutAfterAuthenticationAndResumed)
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		handleRedirect(intent)
	}

	private fun handleRedirect(intent: Intent) {
		intent.data?.let {
			if (it.host == REDIRECT_HOST && it.scheme == "$REDIRECT_SCHEME_PREFIX${BuildConfig.BOX_CLIENT_ID}") {
				oAuthResultReceived = true
				val code = it.getQueryParameter("code")
				if (code != null && it.getQueryParameter("state") == oAuthState) {
					val result = Intent()
					result.putExtra(CloudConnectionListPresenter.BOX_OAUTH_AUTH_CODE, code)
					setResult(android.app.Activity.RESULT_OK, result)
				} else {
					Toast.makeText(this, R.string.error_authentication_failed, Toast.LENGTH_LONG).show()
					Timber.tag("AuthenticateBoxActivity").i("Authentication failed as the code is null or the state doesn't match, error: %s", it.getQueryParameter("error"))
				}
				finish()
			} else {
				Timber.tag("AuthenticateBoxActivity").e("Tried to call activity using a different redirect scheme")
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		cancelAuthenticationHandler.removeCallbacksAndMessages(null)
	}

	companion object {

		private const val OAUTH_STATE = "oAuthState"
		private const val REDIRECT_SCHEME_PREFIX = "boxsdk-"
		private const val REDIRECT_HOST = "boxsdkoauth2redirect"
	}
}
