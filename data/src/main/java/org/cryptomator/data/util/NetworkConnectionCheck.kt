package org.cryptomator.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.cryptomator.domain.Cloud
import org.cryptomator.domain.exception.NetworkConnectionException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkConnectionCheck @Inject internal constructor(private val context: Context) {

	@Throws(NetworkConnectionException::class)
	fun assertConnectionIsPresent(cloud: Cloud) {
		if (cloud.requiresNetwork() && !isPresent) {
			throw NetworkConnectionException()
		}
	}

	val isPresent: Boolean
		get() {
			val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
			val networkInfo = connectivityManager.activeNetworkInfo
			return (networkInfo != null && networkInfo.isConnectedOrConnecting)
		}

	fun checkWifiOnAndConnected(): Boolean {
		val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		val activeNetwork = connectivityManager.activeNetwork
		return connectivityManager.getNetworkCapabilities(activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
	}
}
