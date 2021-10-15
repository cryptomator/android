package org.cryptomator.presentation.ui.activity

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.cryptomator.generator.Activity
import org.cryptomator.presentation.BuildConfig
import org.cryptomator.presentation.R
import org.cryptomator.presentation.presenter.CloudConnectionListPresenter
import java.util.TreeMap
import timber.log.Timber

@Activity
class AuthenticatePCloudActivity : BaseActivity() {

	override fun setupView() {
		val uri = Uri.parse("https://my.pcloud.com/oauth2/authorize")
			.buildUpon()
			.appendQueryParameter("response_type", "token")
			.appendQueryParameter("client_id", BuildConfig.PCLOUD_CLIENT_ID)
			.appendQueryParameter("redirect_uri", "pcloudoauth://redirect")
			.build()

		startActivityForResult(Intent(Intent.ACTION_VIEW, uri), 25)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
		super.onActivityResult(requestCode, resultCode, intent)
		finish()
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		intent.data?.let {
			if(it.host == "redirect" && it.scheme == "pcloudoauth") {
				val parameters = parseUrlFragmentParameters(it)
				val accessToken = parameters["access_token"]
				val hostname = parameters["hostname"]
				if (accessToken != null && hostname != null) {
					val result = Intent()
					result.putExtra(CloudConnectionListPresenter.PCLOUD_OAUTH_AUTH_CODE, accessToken)
					result.putExtra(CloudConnectionListPresenter.PCLOUD_HOSTNAME, hostname)
					setResult(android.app.Activity.RESULT_OK, result)
					finish()
				} else {
					Toast.makeText(this, R.string.error_authentication_failed, Toast.LENGTH_LONG).show()
				}
			} else {
				Timber.tag("AuthenticatePCloudActivity").e("Tried to call activity using a different redirect scheme")
			}
		}
	}

	private fun parseUrlFragmentParameters(url: Uri): Map<String, String> {
		url.fragment?.let {
			val parameters: MutableMap<String, String> = TreeMap()
			val keyPairs = it.split("&".toRegex()).toTypedArray()
			keyPairs.forEach { keyPair ->
				val delimiterIndex = keyPair.indexOf('=')
				parameters[keyPair.substring(0, delimiterIndex)] = keyPair.substring(delimiterIndex + 1, keyPair.length)
			}
			return parameters
		}
		return emptyMap()
	}
}
