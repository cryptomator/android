package org.cryptomator.data.cloud.webdav.network

import org.cryptomator.domain.exception.FatalBackendException
import java.security.GeneralSecurityException
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

internal object SSLSocketFactories {

	fun from(trustManager: X509TrustManager): SSLSocketFactory {
		return try {
			val tlsVersion = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
				"TLSv1.3"
			} else {
				"TLSv1.2"
			}
			val sslContext = SSLContext.getInstance(tlsVersion)
			sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
			sslContext.socketFactory
		} catch (e: GeneralSecurityException) {
			throw FatalBackendException(e)
		}
	}
}
