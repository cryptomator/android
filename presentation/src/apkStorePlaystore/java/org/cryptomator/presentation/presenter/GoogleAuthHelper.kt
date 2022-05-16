package org.cryptomator.presentation.presenter

import android.content.Context
import android.content.Intent
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes

object GoogleAuthHelper {

	fun getChooseAccountIntent(context: Context): Intent? {
		return GoogleAccountCredential.usingOAuth2(context, setOf(DriveScopes.DRIVE)).newChooseAccountIntent()
	}
}
