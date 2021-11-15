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
import org.cryptomator.presentation.service.AutoUploadService.cancelAutoUploadIntent
import org.cryptomator.presentation.ui.activity.VaultListActivity
import org.cryptomator.presentation.util.ResourceHelper.Companion.getColor
import org.cryptomator.presentation.util.ResourceHelper.Companion.getString
import java.lang.String.format

class AutoUploadNotification(private val context: Context, private val amountOfPictures: Int) {

	private val builder: NotificationCompat.Builder
	private var notificationManager: NotificationManager? = null
	private var alreadyUploadedPictures = 0

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
			.setContentTitle(context.getString(R.string.notification_auto_upload_title)) //
			.setSmallIcon(R.drawable.background_splash_cryptomator) //
			.setColor(getColor(R.color.colorPrimary)) //
			.addAction(cancelNowAction())
			.setGroup(NOTIFICATION_GROUP_KEY)
			.setOngoing(true)
	}

	private fun cancelNowAction(): NotificationCompat.Action {
		return NotificationCompat.Action.Builder( //
			R.drawable.ic_lock, //
			getString(R.string.notification_cancel_auto_upload), //
			cancelNowIntent() //
		).build()
	}

	private fun cancelNowIntent(): PendingIntent {
		val intentAction = cancelAutoUploadIntent(context)
		return PendingIntent.getService(context, 0, intentAction, FLAG_CANCEL_CURRENT)
	}

	private fun startTheActivity(): PendingIntent {
		val startTheActivity = Intent(context, VaultListActivity::class.java)
		startTheActivity.action = ACTION_MAIN
		startTheActivity.flags = FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK
		return PendingIntent.getActivity(context, 0, startTheActivity, FLAG_CANCEL_CURRENT)
	}

	fun update(progress: Int) {
		builder.setContentIntent(startTheActivity())
		builder //
			.setContentText( //
				String.format(
					context.getString(R.string.notification_auto_upload_message), //
					alreadyUploadedPictures + 1, //
					amountOfPictures
				)
			) //
			.setProgress(100, progress, false)
		show()
	}

	fun updateFinishedFile() {
		alreadyUploadedPictures += 1
		update(100)
	}

	fun showFolderMissing() {
		showErrorWithMessage(context.getString(R.string.notification_auto_upload_failed_due_to_folder_not_exists))
	}

	fun showVaultLockedDuringUpload() {
		showErrorWithMessage(context.getString(R.string.notification_auto_upload_failed_due_to_vault_locked))
	}

	fun showGeneralErrorDuringUpload() {
		showErrorWithMessage(context.getString(R.string.notification_auto_upload_failed_general_error))
	}

	fun showVaultNotFoundNotification() {
		showErrorWithMessage(context.getString(R.string.notification_auto_upload_failed_due_to_vault_not_found))
	}

	private fun showErrorWithMessage(message: String) {
		builder.setContentIntent(startTheActivity())
		builder //
			.setContentTitle(context.getString(R.string.notification_auto_upload_failed_title))
			.setContentText(message) //
			.setProgress(0, 0, false)
			.setAutoCancel(true)
			.setOngoing(false)
			.mActions.clear()
		show()
	}

	fun showUploadFinished(size: Int) {
		builder.setContentIntent(startTheActivity())
		builder //
			.setContentTitle(context.getString(R.string.notification_auto_upload_finished_title))
			.setContentText(format(context.getString(R.string.notification_auto_upload_finished_message), size)) //
			.setProgress(0, 0, false)
			.setAutoCancel(true)
			.setOngoing(false)
			.mActions.clear()
		show()
	}

	fun show() {
		notificationManager?.notify(NOTIFICATION_ID, builder.build())
	}

	fun hide() {
		notificationManager?.cancel(NOTIFICATION_ID)
	}

	companion object {

		private const val NOTIFICATION_ID = 94874
		private const val NOTIFICATION_CHANNEL_ID = "65478"
		private const val NOTIFICATION_CHANNEL_NAME = "Cryptomator"
		private const val NOTIFICATION_GROUP_KEY = "CryptomatorGroup"
	}
}
