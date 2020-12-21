package org.cryptomator.data.cloud.webdav.network;

import org.cryptomator.data.util.X509CertificateHelper;
import org.cryptomator.domain.exception.FatalBackendException;
import org.cryptomator.domain.exception.NotTrustableCertificateException;
import org.cryptomator.util.Optional;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;

import okhttp3.CertificatePinner;

/**
 * An {@link X509TrustManager} which always trusts one specific certificate but denies all others.
 */
class PinningTrustManager implements X509TrustManager {

	private final String expectedPin;

	/**
	 * Creates a {@code PinningTrustManager} which trusts the provided certificate.
	 *
	 * @param trustedCertPemEncoded the {@link X509Certificate} to trust in PEM encoded form
	 */
	public PinningTrustManager(String trustedCertPemEncoded) {
		try {
			X509Certificate trustedCert = X509CertificateHelper.convertFromPem(trustedCertPemEncoded);
			expectedPin = CertificatePinner.pin(trustedCert);
		} catch (CertificateException e) {
			throw new FatalBackendException(e);
		}
	}

	@Override
	public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (!isPinnedCertificate(chain[0])) {
			throw new NotTrustableCertificateException(X509CertificateHelper.convertToPem(chain[0]));
		}
	}

	@Override
	public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
		if (!isPinnedCertificate(chain[0])) {
			throw new NotTrustableCertificateException(X509CertificateHelper.convertToPem(chain[0]));
		}
	}

	private boolean isPinnedCertificate(X509Certificate certificate) {
		return expectedPin.equals(CertificatePinner.pin(certificate));
	}

	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[0];
	}

	/**
	 * @return a HostnameVerifier accepting any host when the pinned certificate is used and denying all other
	 */
	public HostnameVerifier hostnameVerifier() {
		return new HostnameVerifier() {
			@Override
			public boolean verify(String hostname, SSLSession session) {
				Optional<X509Certificate> peerX509Cert = peerX509Cert(session);
				if (peerX509Cert.isPresent()) {
					return isPinnedCertificate(peerX509Cert.get());
				} else {
					return false;
				}
			}

			private Optional<X509Certificate> peerX509Cert(SSLSession session) {
				try {
					Certificate[] certificates = session.getPeerCertificates();
					if (certificates != null && certificates.length > 0 && certificates[0] instanceof X509Certificate) {
						return Optional.of((X509Certificate) certificates[0]);
					}
				} catch (SSLPeerUnverifiedException e) {
					// leads to return of Optional.empty(), intended!
				}
				return Optional.empty();
			}
		};
	}
}
