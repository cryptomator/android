package org.cryptomator.presentation.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.core.app.NotificationCompat
import org.cryptomator.presentation.R
import org.cryptomator.presentation.ui.activity.VaultListActivity
import org.cryptomator.presentation.util.ResourceHelper.Companion.getColor
import java.lang.String.format
import timber.log.Timber

class UploadNotification(private val context: Context, private val totalFiles: Int) {

	private val builder: NotificationCompat.Builder
	private var notificationManager: NotificationManager? = null
	private var completedFiles = 0

	init {
		this.notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			val notificationChannel = NotificationChannel(
				NOTIFICATION_CHANNEL_ID,
				NOTIFICATION_CHANNEL_NAME,
				IMPORTANCE_LOW
			)
			notificationManager?.createNotificationChannel(notificationChannel)
		}

		this.builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
			.setContentTitle(context.getString(R.string.notification_upload_title))
			.setSmallIcon(R.drawable.ic_notification)
			.setColor(getColor(R.color.colorPrimary))
			.addAction(cancelAction())
			.setGroup(NOTIFICATION_GROUP_KEY)
			.setOngoing(true)
	}

	private fun cancelAction(): NotificationCompat.Action {
		val intentAction = UploadService.cancelUploadIntent(context)
		val cancelIntent = PendingIntent.getService(context, 0, intentAction, FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
		return NotificationCompat.Action.Builder(
			R.drawable.ic_lock,
			context.getString(R.string.notification_upload_cancel),
			cancelIntent
		).build()
	}

	private fun startTheActivity(): PendingIntent {
		val startTheActivity = Intent(context, VaultListActivity::class.java)
		startTheActivity.action = ACTION_MAIN
		startTheActivity.flags = FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
		return PendingIntent.getActivity(context, 0, startTheActivity, FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
	}

	fun update(progress: Int) {
		val currentFile = (completedFiles + 1).coerceAtMost(totalFiles)
		builder
			.setContentIntent(startTheActivity())
			.setContentText(
				format(
					context.getString(R.string.notification_upload_message),
					currentFile,
					totalFiles
				)
			)
			.setProgress(100, progress, false)
		show()
	}

	fun updateFinishedFile() {
		completedFiles += 1
		update(100)
	}

	fun showUploadFinished(count: Int) {
		builder
			.setContentIntent(startTheActivity())
			.setContentTitle(context.getString(R.string.notification_upload_finished_title))
			.setContentText(context.resources.getQuantityString(R.plurals.notification_upload_finished_message, count, count))
			.setProgress(0, 0, false)
			.setAutoCancel(true)
			.setOngoing(false)
			.clearActions()
		show()
	}

	fun showUploadInterrupted() {
		Timber.tag("UploadNotification").i("Show upload interrupted notification")
		showErrorWithMessage(context.getString(R.string.notification_upload_interrupted_message))
	}

	fun showVaultLockedDuringUpload() {
		Timber.tag("UploadNotification").i("Show vault locked during upload notification")
		showErrorWithMessage(context.getString(R.string.notification_upload_failed_vault_locked))
	}

	fun showGeneralErrorDuringUpload() {
		Timber.tag("UploadNotification").i("Show general error during upload notification")
		showErrorWithMessage(context.getString(R.string.notification_upload_failed_general_error))
	}

	private fun showErrorWithMessage(message: String) {
		builder
			.setContentIntent(startTheActivity())
			.setContentTitle(context.getString(R.string.notification_upload_failed_title))
			.setContentText(message)
			.setProgress(0, 0, false)
			.setAutoCancel(true)
			.setOngoing(false)
			.clearActions()
		show()
	}

	fun show() {
		notificationManager?.notify(NOTIFICATION_ID, builder.build())
	}

	fun hide() {
		notificationManager?.cancel(NOTIFICATION_ID)
	}

	fun getNotification() = builder.build()

	companion object {

		const val NOTIFICATION_ID = 94875
		private const val NOTIFICATION_CHANNEL_ID = "65479"
		private const val NOTIFICATION_CHANNEL_NAME = "Cryptomator Upload"
		private const val NOTIFICATION_GROUP_KEY = "CryptomatorUploadGroup"
	}
}
