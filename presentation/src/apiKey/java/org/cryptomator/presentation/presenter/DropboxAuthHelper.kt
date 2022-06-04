package org.cryptomator.presentation.presenter

import android.content.Context
import com.dropbox.core.android.Auth
import org.cryptomator.presentation.BuildConfig

object DropboxAuthHelper {

	fun startOAuth2Authentication(context: Context) {
		Auth.startOAuth2Authentication(context, BuildConfig.DROPBOX_API_KEY)
	}

	fun getOAuth2Token(): String? {
		return Auth.getOAuth2Token()
	}

}
