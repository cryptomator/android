package org.cryptomator.presentation.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.cryptomator.data.cloud.crypto.Cryptors;
import org.cryptomator.presentation.docprovider.CryptomatorDocumentsProvider;
import org.cryptomator.util.LockTimeout;
import org.cryptomator.util.SharedPreferencesHandler;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import timber.log.Timber;

public class CryptorsService extends Service {

	public static final String SCREEN_AND_VAULT_LOCKED = "CRYPTOMATOR_SCREEN_AND_VAULT_LOCKED";
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
	/**
	 * Whether the screen went off while transfers were running, so that the vaults lock once they have finished.
	 */
	private volatile boolean screenOffLockPending = false;
	private final Thread worker = new Thread(new Runnable() {
		@Override
		public void run() {
			while (running) {
				try {
					waitUntilVaultsUnlockedAndInBackground();
					if (!lockSuspended) {
						boolean transfersRunning = sharedPreferencesHandler.lockGracefully() && transfersRunning();
						if (transfersRunning) {
							// a running transfer counts as use, so the timeout runs from when the last one has finished
							autolockTimeout.used();
						}
						if (screenOffLockPending) {
							if (!transfersRunning) {
								Timber.tag("CryptorsService").i("Transfers finished after the screen went off");
								lockOnScreenOff();
							}
						} else if (autolockTimeout.expired()) {
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

	public static Intent lockAllIntent(Context context) {
		Intent lockAllIntent = new Intent(context, CryptorsService.class);
		lockAllIntent.setAction(ACTION_LOCK_ALL);
		return lockAllIntent;
	}

	private void waitUntilVaultsUnlockedAndInBackground() throws InterruptedException {
		unlockedLock.lock();
		try {
			if (cryptors.isEmpty() || (autolockTimeout.isDisabled() && !screenOffLockPending)) {
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
		filter.addAction(Intent.ACTION_USER_PRESENT);
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

	/**
	 * Called only when vaults were unlocked or locked, see {@link Cryptors.Default#setOnChangeListener}, so that e.g.
	 * the screen going off with no vault unlocked doesn't create the documents caches on the main thread.
	 */
	private void onUnlockCountChanged(int unlocked) {
		if (unlocked == 0) {
			CryptomatorDocumentsProvider.evictCopies();
		}
		CryptomatorDocumentsProvider.forgetResumedUploads();
		notification.setUnlockedCount(unlocked);
		notification.update();
		signalVaultsUnlockedAndInBackgroundIfRequired();
		updateDocumentProvider();
	}

	/**
	 * Notifies observers of all roots and documents, so that the Files app shows what was locked or unlocked.
	 */
	private void updateDocumentProvider() {
		Uri providerUri = new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(CryptomatorDocumentsProvider.AUTHORITY).build();
		getContentResolver().notifyChange(providerUri, null);
	}

	private void onAppInForegroundChanged(boolean appInForeground) {
		autolockTimeout.setAppIsActive(appInForeground);
		notification.update();
		signalVaultsUnlockedAndInBackgroundIfRequired();
	}

	private void signalVaultsUnlockedAndInBackgroundIfRequired() {
		unlockedLock.lock();
		try {
			if (!cryptors.isEmpty() && (!autolockTimeout.isDisabled() || screenOffLockPending)) {
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

	private boolean transfersRunning() {
		return CryptomatorDocumentsProvider.hasTransfers();
	}

	private void lockOnScreenOff() {
		screenOffLockPending = false;
		destroyCryptorsAndHideNotification();
		stopCryptorsService();
		// notify BrowseFilesActivity to finish because of #320
		LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(SCREEN_AND_VAULT_LOCKED));
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

		/**
		 * Starts the automatic locking's timeout over, e.g. because another app uses a vault through the documents
		 * provider.
		 */
		public void vaultsUsed() {
			autolockTimeout.used();
		}
	}

	class ScreenLockReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if (Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
				// the user is back before the transfers finished, the timeout applies again
				screenOffLockPending = false;
			} else if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction()) && //
					sharedPreferencesHandler.lockOnScreenOff() && //
					!lockSuspended) {
				// transfers need an unlocked vault, so with none the documents caches aren't asked, which would create them here
				if (sharedPreferencesHandler.lockGracefully() && !cryptors.isEmpty() && transfersRunning()) {
					Timber.tag("CryptorsService").i("ScreenLock received, locking once the running transfers have finished");
					screenOffLockPending = true;
					signalVaultsUnlockedAndInBackgroundIfRequired();
				} else {
					Timber.tag("CryptorsService").i("ScreenLock received, destroying cryptors and shutting down service");
					lockOnScreenOff();
				}
			}
		}
	}
}
