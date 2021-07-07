package org.cryptomator.data.util

import android.util.Base64
import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils
import java.io.ByteArrayInputStream
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

object X509CertificateHelper {

	private const val CERT_BEGIN = "-----BEGIN CERTIFICATE-----\n"
	private const val CERT_END = "-----END CERTIFICATE-----"

	@JvmStatic
	@Throws(CertificateEncodingException::class)
	fun convertToPem(cert: X509Certificate): String {
		val pemCertPre = Base64.encodeToString(cert.encoded, Base64.DEFAULT)
		return CERT_BEGIN + pemCertPre + CERT_END
	}

	@Throws(CertificateException::class)
	fun convertFromPem(pem: String): X509Certificate {
		val decoded = Base64 //
			.decode(pem.replace(CERT_BEGIN.toRegex(), "").replace(CERT_END.toRegex(), ""), Base64.DEFAULT)
		return CertificateFactory //
			.getInstance("X.509") //
			.generateCertificate(ByteArrayInputStream(decoded)) as X509Certificate
	}

	@Throws(CertificateEncodingException::class)
	fun getFingerprintFormatted(certificate: X509Certificate): String {
		var hash = String(Hex.encodeHex(DigestUtils.sha1(certificate.encoded))) //
			.uppercase() //
			.replace("(.{2})".toRegex(), "$1:")
		hash = hash.substring(0, hash.length - 1)
		return "SHA-256 $hash"
	}
}
