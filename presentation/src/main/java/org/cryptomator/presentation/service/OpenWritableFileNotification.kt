package org.cryptomator.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.content.Context
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.net.Uri
import androidx.core.app.NotificationCompat
import org.cryptomator.presentation.R
import org.cryptomator.presentation.intent.Intents.vaultListIntent
import org.cryptomator.presentation.presenter.ContextHolder
import org.cryptomator.presentation.util.ResourceHelper
import org.cryptomator.presentation.util.ResourceHelper.Companion.getColor

class OpenWritableFileNotification(private val context: Context, private val uriToOpenendFile: Uri) {

	private val builder: NotificationCompat.Builder
	private var notificationManager: NotificationManager? = null

	init {
		this.notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			val notificationChannel = NotificationChannel( //
				NOTIFICATION_CHANNEL_ID, //
				NOTIFICATION_CHANNEL_NAME, //
				IMPORTANCE_LOW
			)
			notificationManager?.createNotificationChannel(notificationChannel)
		}

		this.builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID) //
			.setContentTitle(context.getString(R.string.notification_open_writable_file_title)) //
			.setContentText(context.getString(R.string.notification_open_writable_file_message)) //
			.setSmallIcon(R.drawable.background_splash_cryptomator) //
			.setColor(getColor(R.color.colorPrimary)) //
			.setGroup(NOTIFICATION_GROUP_KEY)
			.setOngoing(true)
			.addAction(cancelNowAction())
	}

	private fun cancelNowAction(): NotificationCompat.Action {
		return NotificationCompat.Action.Builder( //
			R.drawable.ic_lock, //
			ResourceHelper.getString(R.string.notification_cancel_open_writable_file), //
			cancelNowIntent() //
		).build()
	}

	private fun cancelNowIntent(): PendingIntent {
		context.revokeUriPermission(uriToOpenendFile, FLAG_GRANT_WRITE_URI_PERMISSION or FLAG_GRANT_READ_URI_PERMISSION)
		val startTheActivity = vaultListIntent().withStopEditFileNotification(true).build(context as ContextHolder)
		return PendingIntent.getActivity(context, 0, startTheActivity, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
	}

	fun show() {
		notificationManager?.notify(NOTIFICATION_ID, builder.build())
	}

	fun hide() {
		notificationManager?.cancel(NOTIFICATION_ID)
	}

	companion object {

		private const val NOTIFICATION_ID = 94875
		private const val NOTIFICATION_CHANNEL_ID = "65478"
		private const val NOTIFICATION_CHANNEL_NAME = "Cryptomator"
		private const val NOTIFICATION_GROUP_KEY = "CryptomatorGroup"
	}
}
