package org.cryptomator.data.cloud.webdav.network;

import org.cryptomator.domain.exception.NotTrustableCertificateException;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import static org.cryptomator.data.util.X509CertificateHelper.convertToPem;

class DefaultTrustManager implements X509TrustManager {

	private final X509TrustManager delegate;

	public DefaultTrustManager() {
		this.delegate = findDefaultTrustManager();
	}

	private static X509TrustManager findDefaultTrustManager() {
		try {
			return tryToFindDefaultTrustManager();
		} catch (KeyStoreException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("Failed to obtain default trust manager", e);
		}
	}

	private static X509TrustManager tryToFindDefaultTrustManager() throws NoSuchAlgorithmException, KeyStoreException {
		TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
		trustManagerFactory.init((KeyStore) null);
		for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
			if (trustManager instanceof X509TrustManager) {
				return (X509TrustManager) trustManager;
			}
		}
		throw new IllegalStateException("Failed to obtain default trust manager: No X509TrustManager available.");
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			delegate.checkClientTrusted(chain, authType);
		} catch (CertificateException e) {
			throw new NotTrustableCertificateException(convertToPem(chain[0]), e);
		}
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		try {
			delegate.checkServerTrusted(chain, authType);
		} catch (CertificateException e) {
			throw new NotTrustableCertificateException(convertToPem(chain[0]), e);
		}
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return delegate.getAcceptedIssuers();
	}
}
