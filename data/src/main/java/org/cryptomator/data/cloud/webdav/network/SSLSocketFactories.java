package org.cryptomator.data.cloud.webdav.network;

import org.cryptomator.domain.exception.FatalBackendException;

import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

class SSLSocketFactories {

	public static SSLSocketFactory from(X509TrustManager trustManager) {
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[] {trustManager}, null);
			return sslContext.getSocketFactory();
		} catch (GeneralSecurityException e) {
			throw new FatalBackendException(e);
		}
	}

}
