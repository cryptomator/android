package org.cryptomator.presentation.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import org.cryptomator.presentation.R;
import org.cryptomator.presentation.ui.activity.VaultListActivity;
import org.cryptomator.presentation.util.ResourceHelper;

import timber.log.Timber;

import static android.app.NotificationManager.IMPORTANCE_LOW;
import static android.app.PendingIntent.FLAG_CANCEL_CURRENT;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static java.lang.Math.round;
import static java.lang.String.format;
import static java.util.Locale.getDefault;

class UnlockedNotification {

	private static final int NOTIFICATION_ID = 94873;
	private static final String NOTIFICATION_CHANNEL_ID = "65478";
	private static final String NOTIFICATION_CHANNEL_NAME = "Cryptomator";
	private static final String NOTIFICATION_GROUP_KEY = "CryptomatorGroup";

	private final Service service;
	private final NotificationCompat.Builder builder;

	private int unlocked = 0;
	private final AutolockTimeout autolockTimeout;

	public UnlockedNotification(Service service, AutolockTimeout autolockTimeout) {
		this.service = service;

		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			NotificationManager notificationManager = (NotificationManager) service.getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				NotificationChannel notificationChannel = new NotificationChannel( //
						NOTIFICATION_CHANNEL_ID, //
						NOTIFICATION_CHANNEL_NAME, //
						IMPORTANCE_LOW);
				notificationManager.createNotificationChannel(notificationChannel);
			} else {
				Timber.tag("UnlockedNotification").e("Failed to get notification service for creating notification channel");
			}
		}

		this.builder = new NotificationCompat.Builder(service, NOTIFICATION_CHANNEL_ID) //
				.setSmallIcon(R.mipmap.ic_launcher) //
				.setColor(ResourceHelper.Companion.getColor(R.color.colorPrimary)) //
				.addAction(lockNowAction()) //
				.setGroup(NOTIFICATION_GROUP_KEY) //
				.setOngoing(true);
		this.autolockTimeout = autolockTimeout;
	}

	private NotificationCompat.Action lockNowAction() {
		return new NotificationCompat.Action.Builder( //
				R.drawable.ic_lock, //
				ResourceHelper.Companion.getString(R.string.notification_lock_all), //
				lockNowIntent() //
		).build();
	}

	private PendingIntent lockNowIntent() {
		return PendingIntent.getService( //
				service.getApplicationContext(), //
				0, //
				CryptorsService.lockAllIntent(service.getApplicationContext()), //
				FLAG_CANCEL_CURRENT);
	}

	private PendingIntent startTheActivity() {
		Intent startTheActivity = new Intent(service, VaultListActivity.class);
		startTheActivity.setAction(ACTION_MAIN);
		startTheActivity.setFlags(FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK);
		return PendingIntent.getActivity(service, 0, startTheActivity, 0);
	}

	public void setUnlockedCount(int unlocked) {
		this.unlocked = unlocked;
	}

	public void update() {
		builder.setContentIntent(startTheActivity());
		if (autolockTimeout.isDisabled()) {
			builder //
					.setContentText(null) //
					.setProgress(0, 0, false);
		} else {
			builder //
					.setContentText(service.getString(R.string.notification_timeout, readableAutoLockTimeout())) //
					.setProgress((int) autolockTimeout.configuredTimeout(), autolockTimeout.timeRemaining(), false);
		}
		if (unlocked == 0) {
			hide();
		} else {
			builder.setContentTitle(service.getString(R.string.notification_unlocked, unlocked));
			show();
		}
	}

	public void show() {
		service.startForeground(NOTIFICATION_ID, builder.build());
	}

	public void hide() {
		service.stopForeground(true);
	}

	private String readableAutoLockTimeout() {
		int seconds = autolockTimeout.timeRemaining() / 1000;
		if (seconds < 60) {
			return format(getDefault(), "%ds", seconds);
		}
		return format(getDefault(), "%dm", round(seconds / 60.0f));
	}

}
