package org.cryptomator.data.cloud.webdav.network

import org.cryptomator.data.util.X509CertificateHelper
import org.cryptomator.domain.exception.FatalBackendException
import org.cryptomator.domain.exception.NotTrustableCertificateException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager
import okhttp3.CertificatePinner

/**
 * An [X509TrustManager] which always trusts one specific certificate but denies all others.
 */
internal class PinningTrustManager(trustedCertPemEncoded: String) : X509TrustManager {

	private var expectedPin: String? = null

	@Throws(CertificateException::class)
	override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
		if (!isPinnedCertificate(chain[0])) {
			throw NotTrustableCertificateException(X509CertificateHelper.convertToPem(chain[0]))
		}
	}

	@Throws(CertificateException::class)
	override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
		if (!isPinnedCertificate(chain[0])) {
			throw NotTrustableCertificateException(X509CertificateHelper.convertToPem(chain[0]))
		}
	}

	private fun isPinnedCertificate(certificate: X509Certificate): Boolean {
		return expectedPin == CertificatePinner.pin(certificate)
	}

	override fun getAcceptedIssuers(): Array<X509Certificate?> {
		return arrayOfNulls(0)
	}

	/**
	 * @return a HostnameVerifier accepting any host when the pinned certificate is used and denying all other
	 */
	fun hostnameVerifier(): HostnameVerifier {

		return object : HostnameVerifier {

			override fun verify(hostname: String, session: SSLSession): Boolean {
				return peerX509Cert(session)?.let { isPinnedCertificate(it) } ?: false
			}

			private fun peerX509Cert(session: SSLSession): X509Certificate? {
				try {
					val certificates = session.peerCertificates
					if (certificates != null && certificates.isNotEmpty() && certificates[0] is X509Certificate) {
						return certificates[0] as X509Certificate
					}
				} catch (e: SSLPeerUnverifiedException) {
					// leads to return of null, intended!
				}
				return null
			}
		}
	}

	/**
	 * Creates a `PinningTrustManager` which trusts the provided certificate.
	 *
	 * @param trustedCertPemEncoded the [X509Certificate] to trust in PEM encoded form
	 */
	init {
		expectedPin = try {
			val trustedCert = X509CertificateHelper.convertFromPem(trustedCertPemEncoded)
			CertificatePinner.pin(trustedCert)
		} catch (e: CertificateException) {
			throw FatalBackendException(e)
		}
	}
}
