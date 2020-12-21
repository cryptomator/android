package org.cryptomator.data.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import org.cryptomator.domain.Cloud;
import org.cryptomator.domain.exception.NetworkConnectionException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NetworkConnectionCheck {

	private final Context context;

	@Inject
	NetworkConnectionCheck(Context context) {
		this.context = context;
	}

	public void assertConnectionIsPresent(Cloud cloud) throws NetworkConnectionException {
		if (cloud.requiresNetwork() && !isPresent()) {
			throw new NetworkConnectionException();
		}
	}

	public boolean isPresent() {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
		return networkInfo != null //
				&& networkInfo.isConnectedOrConnecting();
	}

	public boolean checkWifiOnAndConnected() {
		ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
			Network activeNetwork = connectivityManager.getActiveNetwork();
			return connectivityManager.getNetworkCapabilities(activeNetwork).hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
		} else {
			final WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
			if (wifiManager.isWifiEnabled()) {
				return wifiManager.getConnectionInfo().getNetworkId() != -1; // fails on devices post 8.x
			}
			return false;
		}
	}
}
