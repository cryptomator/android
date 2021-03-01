package org.cryptomator.presentation.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.cryptomator.data.cloud.crypto.Cryptors;
import org.cryptomator.presentation.util.FileUtil;
import org.cryptomator.util.Consumer;
import org.cryptomator.util.LockTimeout;
import org.cryptomator.util.SharedPreferencesHandler;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

public class CryptorsService extends Service {

	private static final String ACTION_LOCK_ALL = "CRYPTOMATOR_LOCK_ALL";
	private final Cryptors.Default cryptors = new Cryptors.Default();
	private final AutolockTimeout autolockTimeout = new AutolockTimeout();
	private final Lock unlockedLock = new ReentrantLock();
	private final Condition vaultsUnlockedAndInBackground = unlockedLock.newCondition();
	private SharedPreferencesHandler sharedPreferencesHandler;
	private UnlockedNotification notification;
	private final Consumer<LockTimeout> onLockTimeoutChanged = this::onLockTimeoutChanged;
	private volatile boolean running = true;
	private volatile boolean lockSuspended = false;
	private final Thread worker = new Thread(new Runnable() {
		@Override
		public void run() {
			while (running) {
				try {
					waitUntilVaultsUnlockedAndInBackground();
					if (!lockSuspended) {
						if (autolockTimeout.expired()) {
							Timber.tag("CryptorsService").i("autolock timeout expired");
							destroyCryptorsAndHideNotification();
						} else {
							notification.update();
						}
					}
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					continue;
				}
			}
		}
	});
	private BroadcastReceiver screenLockReceiver;
	private FileUtil fileUtil;

	public static Intent lockAllIntent(Context context) {
		Intent lockAllIntent = new Intent(context, CryptorsService.class);
		lockAllIntent.setAction(ACTION_LOCK_ALL);
		return lockAllIntent;
	}

	private void waitUntilVaultsUnlockedAndInBackground() throws InterruptedException {
		unlockedLock.lock();
		try {
			if (cryptors.isEmpty() || autolockTimeout.isDisabled()) {
				vaultsUnlockedAndInBackground.await();
			}
		} finally {
			unlockedLock.unlock();
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		Timber.tag("CryptorsService").d("created");
		notification = new UnlockedNotification(this, autolockTimeout);
		sharedPreferencesHandler = new SharedPreferencesHandler(this);
		sharedPreferencesHandler.addLockTimeoutChangedListener(onLockTimeoutChanged);
		worker.start();

		IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
		screenLockReceiver = new ScreenLockReceiver();
		registerReceiver(screenLockReceiver, filter);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Timber.tag("CryptorsService").i("started");
		if (isLockAll(intent)) {
			Timber.tag("CryptorsService").i("Received Lock all intent");
			destroyCryptorsAndStopService();
			return START_NOT_STICKY;
		}
		return START_STICKY;
	}

	private boolean isLockAll(Intent intent) {
		return intent != null //
				&& ACTION_LOCK_ALL.equals(intent.getAction());
	}

	@Override
	public void onDestroy() {
		running = false;
		worker.interrupt();
		Timber.tag("CryptorsService").i("destroyed");
		unregisterReceiver(screenLockReceiver);
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		Timber.tag("CryptorsService").i("App killed by user");
		destroyCryptorsAndStopService();
	}

	private void destroyCryptorsAndStopService() {
		destroyCryptorsAndHideNotification();
		stopCryptorsService();
	}

	private void onUnlockCountChanged(int unlocked) {
		if (unlocked == 0) {
			if (fileUtil != null) {
				fileUtil.cleanupDecryptedFiles();
			}
		}
		notification.setUnlockedCount(unlocked);
		notification.update();
		signalVaultsUnlockedAndInBackgroundIfRequired();
	}

	private void onAppInForegroundChanged(boolean appInForeground) {
		autolockTimeout.setAppIsActive(appInForeground);
		notification.update();
		signalVaultsUnlockedAndInBackgroundIfRequired();
	}

	private void signalVaultsUnlockedAndInBackgroundIfRequired() {
		unlockedLock.lock();
		try {
			if (!cryptors.isEmpty() && !autolockTimeout.isDisabled()) {
				vaultsUnlockedAndInBackground.signal();
			}
		} finally {
			unlockedLock.unlock();
		}
	}

	private void onLockTimeoutChanged(LockTimeout lockTimeout) {
		autolockTimeout.setLockTimeout(lockTimeout);
		notification.update();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return new Binder();
	}

	private void stopCryptorsService() {
		Intent myService = new Intent(CryptorsService.this, CryptorsService.class);
		stopService(myService);
	}

	private void destroyCryptorsAndHideNotification() {
		cryptors.destroyAll();
		notification.hide();
	}

	public class Binder extends android.os.Binder {

		Binder() {
			cryptors.setOnChangeListener(() -> onUnlockCountChanged(cryptors.size()));
		}

		public Cryptors.Default cryptors() {
			return cryptors;
		}

		public void appInForeground(boolean appInForeground) {
			onAppInForegroundChanged(appInForeground);
		}

		public void suspendLock() {
			lockSuspended = true;
		}

		public void unSuspendLock() {
			lockSuspended = false;
		}

		public void setFileUtil(FileUtil mfileUtil) {
			fileUtil = mfileUtil;
		}
	}

	class ScreenLockReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF) && //
					sharedPreferencesHandler.lockOnScreenOff() && //
					!lockSuspended) {
				Timber.tag("CryptorsService").i("ScreenLock received, destroying cryptors and shutting down service");

				destroyCryptorsAndHideNotification();

				stopCryptorsService();
			}
		}
	}
}
