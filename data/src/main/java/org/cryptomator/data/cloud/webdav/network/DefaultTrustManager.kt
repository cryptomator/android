package org.cryptomator.data.cloud.webdav.network

import org.cryptomator.data.util.X509CertificateHelper.convertToPem
import org.cryptomator.domain.exception.NotTrustableCertificateException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

internal class DefaultTrustManager : X509TrustManager {

	private val delegate: X509TrustManager = findDefaultTrustManager()

	@Throws(CertificateException::class)
	override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
		try {
			delegate.checkClientTrusted(chain, authType)
		} catch (e: CertificateException) {
			throw NotTrustableCertificateException(convertToPem(chain[0]), e)
		}
	}

	@Throws(CertificateException::class)
	override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
		try {
			delegate.checkServerTrusted(chain, authType)
		} catch (e: CertificateException) {
			throw NotTrustableCertificateException(convertToPem(chain[0]), e)
		}
	}

	override fun getAcceptedIssuers(): Array<X509Certificate> {
		return delegate.acceptedIssuers
	}

	companion object {

		private fun findDefaultTrustManager(): X509TrustManager {
			return try {
				tryToFindDefaultTrustManager()
			} catch (e: KeyStoreException) {
				throw IllegalStateException("Failed to obtain default trust manager", e)
			} catch (e: NoSuchAlgorithmException) {
				throw IllegalStateException("Failed to obtain default trust manager", e)
			}
		}

		@Throws(NoSuchAlgorithmException::class, KeyStoreException::class)
		private fun tryToFindDefaultTrustManager(): X509TrustManager {
			val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

			trustManagerFactory.init(null as KeyStore?)

			trustManagerFactory.trustManagers.forEach { trustManager ->
				if (trustManager is X509TrustManager) {
					return trustManager
				}
			}

			throw IllegalStateException("Failed to obtain default trust manager: No X509TrustManager available.")
		}
	}

}
