package org.cryptomator.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.cryptomator.presentation.service.CryptorsService
import org.cryptomator.presentation.service.PhotoContentJob
import org.cryptomator.util.SharedPreferencesHandler
import timber.log.Timber

class BootAwareReceiver : BroadcastReceiver() {

	override fun onReceive(context: Context, intent: Intent) {
		when {
			intent.action.equals(Intent.ACTION_SHUTDOWN, ignoreCase = true) -> {
				Timber.tag("BootAwareReceiver").i("Starting shutting down CryptorsService")
				context.stopService(CryptorsService.lockAllIntent(context))
			}
			intent.action.equals(Intent.ACTION_BOOT_COMPLETED, ignoreCase = true) -> {
				if (SharedPreferencesHandler(context).usePhotoUpload()) {
					Timber.tag("BootAwareReceiver").i("Starting AutoUploadJobScheduler")
					PhotoContentJob.scheduleJob(context)
				}
			}
		}
	}
}
